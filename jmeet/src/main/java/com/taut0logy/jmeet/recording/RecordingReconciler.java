package com.taut0logy.jmeet.recording;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** §13.3: what allows webhook delivery to be treated as an optimisation rather than a
 * correctness dependency — anything stuck without a terminal webhook gets reconciled here. */
@Component
public class RecordingReconciler {

    private static final Duration STALE_AFTER = Duration.ofMinutes(10);
    private static final List<RecordingStatus> ACTIVE_STATUSES = List.of(RecordingStatus.RECORDING, RecordingStatus.PROCESSING);

    private final RecordingRepository recordings;
    private final RecordingService recordingService;
    private final EgressPort egress;

    public RecordingReconciler(RecordingRepository recordings, RecordingService recordingService, EgressPort egress) {
        this.recordings = recordings;
        this.recordingService = recordingService;
        this.egress = egress;
    }

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "recording-reconciler", lockAtMostFor = "5m")
    @Transactional
    public void reconcile() {
        Instant staleThreshold = Instant.now().minus(STALE_AFTER);
        for (Recording recording : recordings.findByStatusInAndStartedAtBefore(ACTIVE_STATUSES, staleThreshold)) {
            reconcileOne(recording);
        }
        recordings.findByStatusIn(List.of(RecordingStatus.RECORDING)).forEach(recordingService::stopIfOverMaxDuration);
    }

    private void reconcileOne(Recording recording) {
        if (recording.getEgressId() == null) return;
        Optional<EgressStatusSnapshot> snapshot = egress.getEgress(recording.getEgressId());
        if (snapshot.isPresent()) {
            recordingService.applyEgressStatus(recording.getEgressId(), snapshot.get());
        } else {
            recording.markFailed("egress not found during reconciliation");
        }
    }
}
