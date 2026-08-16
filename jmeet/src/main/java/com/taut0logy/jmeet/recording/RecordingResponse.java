package com.taut0logy.jmeet.recording;

import java.time.Instant;

public record RecordingResponse(String id, String meetingId, RecordingStatus status, Instant startedAt,
        Instant endedAt, Integer durationMs, Long sizeBytes, String downloadUrl) {

    public static RecordingResponse from(Recording recording, String downloadUrl) {
        return new RecordingResponse(recording.getId(), recording.getMeetingId(), recording.getStatus(),
                recording.getStartedAt(), recording.getEndedAt(), recording.getDurationMs(), recording.getSizeBytes(),
                downloadUrl);
    }
}
