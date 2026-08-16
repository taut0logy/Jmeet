package com.taut0logy.jmeet.room.livekit;

import com.taut0logy.jmeet.common.Ids;
import com.taut0logy.jmeet.config.RecordingProperties;
import com.taut0logy.jmeet.recording.EgressPort;
import com.taut0logy.jmeet.recording.EgressStatusSnapshot;
import com.taut0logy.jmeet.recording.RecordingStatus;
import io.livekit.server.EgressServiceClient;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import livekit.LivekitEgress;
import org.springframework.stereotype.Component;
import retrofit2.Call;
import retrofit2.Response;

@Component
class LiveKitEgressAdapter implements EgressPort {

    private final EgressServiceClient client;
    private final TokenService tokenService;
    private final RecordingProperties properties;

    LiveKitEgressAdapter(EgressServiceClient client, TokenService tokenService, RecordingProperties properties) {
        this.client = client;
        this.tokenService = tokenService;
        this.properties = properties;
    }

    @Override
    public String startRoomCompositeEgress(String roomName, String storageKey) {
        String recorderToken = tokenService.mintRecorderToken(roomName, "egress-" + Ids.next());
        String layoutUrl = properties.layoutUrl() + "/" + roomName + "?token=" + recorderToken;

        LivekitEgress.S3Upload.Builder s3 = LivekitEgress.S3Upload.newBuilder()
                .setBucket(properties.bucket())
                .setRegion(properties.region())
                .setForcePathStyle(properties.pathStyle());
        if (properties.endpoint() != null && !properties.endpoint().isBlank()) s3.setEndpoint(properties.endpoint());
        if (properties.accessKey() != null && !properties.accessKey().isBlank()) s3.setAccessKey(properties.accessKey());
        if (properties.secretKey() != null && !properties.secretKey().isBlank()) s3.setSecret(properties.secretKey());

        LivekitEgress.EncodedFileOutput output = LivekitEgress.EncodedFileOutput.newBuilder()
                .setFilepath(storageKey)
                .setS3(s3)
                .build();

        LivekitEgress.EgressInfo info = execute(client.startRoomCompositeEgress(roomName, output, "", null, null,
                false, false, layoutUrl, io.livekit.server.AudioMixing.DEFAULT_MIXING));
        return info.getEgressId();
    }

    @Override
    public void stopEgress(String egressId) {
        execute(client.stopEgress(egressId));
    }

    @Override
    public Optional<EgressStatusSnapshot> getEgress(String egressId) {
        var results = execute(client.listEgress(null, egressId, null));
        return results.stream().findFirst().map(this::toSnapshot);
    }

    EgressStatusSnapshot toSnapshot(LivekitEgress.EgressInfo info) {
        RecordingStatus status = switch (info.getStatus()) {
            case EGRESS_STARTING, EGRESS_ACTIVE -> RecordingStatus.RECORDING;
            case EGRESS_ENDING -> RecordingStatus.PROCESSING;
            case EGRESS_COMPLETE -> RecordingStatus.READY;
            default -> RecordingStatus.FAILED;
        };
        String storageKey = info.getFileResultsCount() > 0 ? info.getFileResults(0).getFilename() : null;
        Integer durationMs = info.getFileResultsCount() > 0
                ? (int) TimeUnit.NANOSECONDS.toMillis(info.getFileResults(0).getDuration()) : null;
        Long sizeBytes = info.getFileResultsCount() > 0 ? info.getFileResults(0).getSize() : null;
        String error = status == RecordingStatus.FAILED ? info.getError() : null;
        return new EgressStatusSnapshot(info.getEgressId(), status, storageKey, durationMs, sizeBytes, error);
    }

    private <T> T execute(Call<T> call) {
        try {
            Response<T> response = call.execute();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("LiveKit egress call failed: " + response.code() + " " + response.message());
            }
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException("failed to reach LiveKit egress service", e);
        }
    }
}
