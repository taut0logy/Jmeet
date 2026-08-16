package com.taut0logy.jmeet.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.taut0logy.jmeet.TestcontainersConfiguration;
import com.taut0logy.jmeet.outbox.OutboxRelay;
import com.taut0logy.jmeet.outbox.OutboxService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, StubJobHandler.class})
class MessagingSpineIntegrationTest {

    @DynamicPropertySource
    static void jobProperties(DynamicPropertyRegistry registry) {
        registry.add("app.jobs.backoff-base", () -> "300ms");
        registry.add("app.jobs.backoff-cap", () -> "1s");
        registry.add("app.jobs.max-attempts", () -> "2");
    }

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private StubJobHandler handler;

    @Autowired
    private JobRecordRepository jobRecords;

    @Test
    void jobSucceedsOnFirstDelivery() {
        String payload = "\"happy-path\"";

        outboxService.dispatchJob(JobType.MEETING_REMINDER, "agg-1", payload);
        outboxRelay.poll();

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(handler.handledPayloads()).contains(payload));
    }

    @Test
    void jobRetriesAfterTransientFailureThenSucceeds() {
        String payload = "\"retry-once\"";
        handler.failNext(payload, 1);

        outboxService.dispatchJob(JobType.MEETING_REMINDER, "agg-2", payload);
        outboxRelay.poll();

        long t0 = System.nanoTime();
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(handler.handledCount(payload)).isEqualTo(1));
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        assertThat(elapsedMs).isGreaterThan(200);
    }

    @Test
    void jobDeadLettersAfterMaxAttempts() {
        String payload = "\"always-fails\"";
        handler.failNext(payload, 100);

        outboxService.dispatchJob(JobType.MEETING_REMINDER, "agg-3", payload);
        outboxRelay.poll();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(
                jobRecords.findAll().stream()
                        .anyMatch(r -> payload.equals(r.getPayload()) && r.getStatus() == JobRecordStatus.DEAD))
                .isTrue());
    }
}
