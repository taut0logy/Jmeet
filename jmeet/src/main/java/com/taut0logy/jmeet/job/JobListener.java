package com.taut0logy.jmeet.job;

import com.rabbitmq.client.Channel;
import com.taut0logy.jmeet.config.JobsProperties;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JobListener {

    private final Map<String, JobHandler> handlers;
    private final JobRecordRepository jobRecords;
    private final RabbitTemplate rabbitTemplate;
    private final JobsProperties properties;

    public JobListener(List<JobHandler> handlerList, JobRecordRepository jobRecords,
            RabbitTemplate rabbitTemplate, JobsProperties properties) {
        // Last-registered wins for a given type, so a test can substitute a handler by
        // importing a bean ordered after the real one (@Order is honored for List<T> injection).
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(h -> h.type().key(), Function.identity(), (first, second) -> second));
        this.jobRecords = jobRecords;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @RabbitListener(queues = "#{@jobQueueNames}")
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        UUID messageId = UUID.fromString(message.getMessageProperties().getMessageId());
        String type = message.getMessageProperties().getReceivedRoutingKey();
        String payload = new String(message.getBody());

        if (jobRecords.findById(messageId).filter(r -> r.getStatus() == JobRecordStatus.SUCCEEDED).isPresent()) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            handle(messageId, type, payload);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            requeueOrDeadLetter(messageId, type, payload, e, message);
            channel.basicAck(deliveryTag, false);
        }
    }

    @Transactional
    void handle(UUID messageId, String type, String payload) throws Exception {
        JobHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalStateException("no handler registered for job type: " + type);
        }
        JobRecord record = jobRecords.findById(messageId).orElseGet(() -> new JobRecord(messageId, type, payload));
        jobRecords.save(record);
        handler.handle(payload);
        record.succeed();
    }

    @Transactional
    void requeueOrDeadLetter(UUID messageId, String type, String payload, Exception error, Message original) {
        JobRecord record = jobRecords.findById(messageId).orElseGet(() -> new JobRecord(messageId, type, payload));
        int attempts = record.getAttempts() + 1;
        String errorMessage = error.getMessage();

        if (attempts >= properties.maxAttempts()) {
            record.die(attempts, errorMessage);
            jobRecords.save(record);
            rabbitTemplate.send(Amqp.DLX_EXCHANGE, type, original);
            return;
        }

        record.retry(attempts, errorMessage);
        jobRecords.save(record);
        long delayMs = Backoff.compute(attempts, properties.backoffBase(), properties.backoffCap()).toMillis();
        Message delayed = Amqp.jsonMessage(messageId.toString(), payload);
        delayed.getMessageProperties().setHeader(Amqp.DELAY_HEADER, delayMs);
        rabbitTemplate.send(Amqp.JOBS_EXCHANGE, type, delayed);
    }
}
