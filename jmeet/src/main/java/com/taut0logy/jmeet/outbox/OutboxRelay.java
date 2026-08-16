package com.taut0logy.jmeet.outbox;

import com.taut0logy.jmeet.config.OutboxProperties;
import com.taut0logy.jmeet.job.Amqp;
import com.taut0logy.jmeet.job.Backoff;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxRelay {

    private static final Duration RETRY_BASE = Duration.ofSeconds(2);
    private static final Duration RETRY_CAP = Duration.ofSeconds(60);

    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxProperties properties;

    public OutboxRelay(OutboxEventRepository repository, RabbitTemplate rabbitTemplate, OutboxProperties properties) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval}")
    @Transactional
    public void poll() {
        List<OutboxEvent> batch = repository.claimPending(properties.batchSize());
        for (OutboxEvent event : batch) {
            dispatch(event);
        }
    }

    private void dispatch(OutboxEvent event) {
        try {
            String messageId = event.getId().toString();
            if (Outbox.isJobDispatch(event)) {
                rabbitTemplate.send(Amqp.JOBS_EXCHANGE, event.getType(), Amqp.jsonMessage(messageId, event.getPayload()));
            } else {
                String routingKey = event.getAggregateType() + "." + event.getType();
                rabbitTemplate.send(Outbox.EVENTS_EXCHANGE, routingKey, Amqp.jsonMessage(messageId, event.getPayload()));
            }
            event.markPublished();
        } catch (Exception e) {
            if (event.getAttempts() + 1 >= properties.maxAttempts()) {
                event.markFailed(e.getMessage());
            } else {
                Duration delay = Backoff.compute(event.getAttempts(), RETRY_BASE, RETRY_CAP);
                event.scheduleRetry(Instant.now().plus(delay), e.getMessage());
            }
        }
    }
}
