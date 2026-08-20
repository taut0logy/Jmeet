package com.taut0logy.jmeet.job;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Drains jobs.dead into job_record so a dead-lettered job is queryable in SQL rather than
 * only visible in the RabbitMQ management UI. A listener rather than a literal poll, the queue
 * already pushes, and JobListener's own synchronous die() call makes this a defensive
 * second path, not the only one: idempotent either way since job_record is keyed by message id. */
@Component
public class DeadLetterDrain {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterDrain.class);

    private final JobRecordRepository jobRecords;

    public DeadLetterDrain(JobRecordRepository jobRecords) {
        this.jobRecords = jobRecords;
    }

    @RabbitListener(queues = Amqp.DEAD_QUEUE)
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            UUID messageId = UUID.fromString(message.getMessageProperties().getMessageId());
            String type = message.getMessageProperties().getReceivedRoutingKey();
            String payload = new String(message.getBody());
            recordDeadLetter(messageId, type, payload);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.warn("failed to drain dead-lettered message into job_record: {}", e.getMessage());
            channel.basicAck(deliveryTag, false);
        }
    }

    @Transactional
    public void recordDeadLetter(UUID messageId, String type, String payload) {
        JobRecord record = jobRecords.findById(messageId).orElseGet(() -> new JobRecord(messageId, type, payload));
        if (record.getStatus() != JobRecordStatus.DEAD) {
            record.die(record.getAttempts(), "dead-lettered");
        }
        jobRecords.save(record);
    }
}
