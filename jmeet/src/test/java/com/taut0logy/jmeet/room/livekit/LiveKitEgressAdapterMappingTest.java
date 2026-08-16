package com.taut0logy.jmeet.room.livekit;

import static org.assertj.core.api.Assertions.assertThat;

import com.taut0logy.jmeet.recording.EgressStatusSnapshot;
import com.taut0logy.jmeet.recording.RecordingStatus;
import java.util.concurrent.TimeUnit;
import livekit.LivekitEgress;
import org.junit.jupiter.api.Test;

class LiveKitEgressAdapterMappingTest {

    private final LiveKitEgressAdapter adapter = new LiveKitEgressAdapter(null, null, null);

    private LivekitEgress.EgressInfo.Builder info(LivekitEgress.EgressStatus status) {
        return LivekitEgress.EgressInfo.newBuilder().setEgressId("eg-1").setStatus(status);
    }

    @Test
    void startingAndActiveMapToRecording() {
        assertThat(adapter.toSnapshot(info(LivekitEgress.EgressStatus.EGRESS_STARTING).build()).status())
                .isEqualTo(RecordingStatus.RECORDING);
        assertThat(adapter.toSnapshot(info(LivekitEgress.EgressStatus.EGRESS_ACTIVE).build()).status())
                .isEqualTo(RecordingStatus.RECORDING);
    }

    @Test
    void endingMapsToProcessing() {
        assertThat(adapter.toSnapshot(info(LivekitEgress.EgressStatus.EGRESS_ENDING).build()).status())
                .isEqualTo(RecordingStatus.PROCESSING);
    }

    @Test
    void completeMapsToReadyWithFileDetails() {
        LivekitEgress.FileInfo file = LivekitEgress.FileInfo.newBuilder()
                .setFilename("recordings/m1/r1.mp4")
                .setDuration(TimeUnit.SECONDS.toNanos(90))
                .setSize(12_345_678L)
                .build();
        LivekitEgress.EgressInfo egressInfo = info(LivekitEgress.EgressStatus.EGRESS_COMPLETE)
                .addFileResults(file)
                .build();

        EgressStatusSnapshot snapshot = adapter.toSnapshot(egressInfo);
        assertThat(snapshot.status()).isEqualTo(RecordingStatus.READY);
        assertThat(snapshot.storageKey()).isEqualTo("recordings/m1/r1.mp4");
        assertThat(snapshot.durationMs()).isEqualTo(90_000);
        assertThat(snapshot.sizeBytes()).isEqualTo(12_345_678L);
        assertThat(snapshot.error()).isNull();
    }

    @Test
    void failedAbortedAndLimitReachedMapToFailedWithError() {
        LivekitEgress.EgressInfo failed = info(LivekitEgress.EgressStatus.EGRESS_FAILED).setError("boom").build();
        EgressStatusSnapshot snapshot = adapter.toSnapshot(failed);
        assertThat(snapshot.status()).isEqualTo(RecordingStatus.FAILED);
        assertThat(snapshot.error()).isEqualTo("boom");

        assertThat(adapter.toSnapshot(info(LivekitEgress.EgressStatus.EGRESS_ABORTED).build()).status())
                .isEqualTo(RecordingStatus.FAILED);
        assertThat(adapter.toSnapshot(info(LivekitEgress.EgressStatus.EGRESS_LIMIT_REACHED).build()).status())
                .isEqualTo(RecordingStatus.FAILED);
    }
}
