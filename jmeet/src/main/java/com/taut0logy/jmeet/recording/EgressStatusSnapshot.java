package com.taut0logy.jmeet.recording;

public record EgressStatusSnapshot(String egressId, RecordingStatus status, String storageKey, Integer durationMs,
        Long sizeBytes, String error) {
}
