package com.taut0logy.jmeet.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.taut0logy.jmeet.TestcontainersConfiguration;
import com.taut0logy.jmeet.outbox.OutboxEvent;
import com.taut0logy.jmeet.outbox.OutboxEventRepository;
import com.taut0logy.jmeet.outbox.OutboxRelay;
import com.taut0logy.jmeet.outbox.OutboxService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ReliabilityIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEvents;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private DeadLetterDrain deadLetterDrain;

    @Autowired
    private JobRecordRepository jobRecords;

    /** §14.5/§18.2: a row already backed off from a prior failure sits in the table with a
     * future next_attempt_at — proving it doesn't gate an unrelated, currently-due row out of
     * the same poll() batch is the actual mechanism behind "ordering is not guaranteed... a
     * failing row backs off instead of blocking everything behind it." */
    @Test
    void poisonedOutboxRowDoesNotDelayUnrelatedEvents() {
        OutboxEvent poisoned = new OutboxEvent("job", "poison-agg-" + System.nanoTime(), JobType.SESSION_CLOSE.key(), "{}");
        poisoned.scheduleRetry(Instant.now().plusSeconds(3600), "simulated prior failure");
        outboxEvents.save(poisoned);

        String readyAggregateId = "ready-agg-" + System.nanoTime();
        outboxService.dispatchJob(JobType.SESSION_CLOSE, readyAggregateId, "{\"sessionId\":\"does-not-exist\"}");

        outboxRelay.poll();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            OutboxEvent ready = outboxEvents.findAll().stream()
                    .filter(e -> readyAggregateId.equals(e.getAggregateId())).findFirst().orElseThrow();
            assertThat(ready.getPublishedAt()).isNotNull();
        });

        OutboxEvent reloaded = outboxEvents.findById(poisoned.getId()).orElseThrow();
        assertThat(reloaded.getPublishedAt()).isNull();
    }

    @Test
    void deadLetterDrainRecordsJobAsDeadAndIsIdempotent() {
        UUID messageId = UUID.randomUUID();
        deadLetterDrain.recordDeadLetter(messageId, JobType.EMAIL_SEND.key(), "{\"poison\":true}");
        deadLetterDrain.recordDeadLetter(messageId, JobType.EMAIL_SEND.key(), "{\"poison\":true}");

        JobRecord record = jobRecords.findById(messageId).orElseThrow();
        assertThat(record.getStatus()).isEqualTo(JobRecordStatus.DEAD);
        assertThat(record.getType()).isEqualTo(JobType.EMAIL_SEND.key());
    }
}
