package com.taut0logy.jmeet.recording;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "recording")
public class Recording {

    @Id
    private String id;

    private String meetingId;
    private String sessionId;
    private String egressId;
    private String startedBy;
    private Instant startedAt;
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    private RecordingStatus status;

    private String layout;
    private String storageKey;
    private Integer durationMs;
    private Long sizeBytes;
    private String error;

    protected Recording() {
    }

    public Recording(String id, String meetingId, String sessionId, String startedBy, String layout) {
        this.id = id;
        this.meetingId = meetingId;
        this.sessionId = sessionId;
        this.startedBy = startedBy;
        this.layout = layout;
        this.status = RecordingStatus.RECORDING;
        this.startedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getMeetingId() {
        return meetingId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getEgressId() {
        return egressId;
    }

    public String getStartedBy() {
        return startedBy;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public RecordingStatus getStatus() {
        return status;
    }

    public String getLayout() {
        return layout;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getError() {
        return error;
    }

    public boolean isTerminal() {
        return status == RecordingStatus.READY || status == RecordingStatus.FAILED;
    }

    public void setEgressId(String egressId) {
        this.egressId = egressId;
    }

    public void markProcessing() {
        if (!isTerminal()) this.status = RecordingStatus.PROCESSING;
    }

    public void markRecordingOrProcessing(boolean active) {
        if (!isTerminal()) this.status = active ? RecordingStatus.RECORDING : RecordingStatus.PROCESSING;
    }

    public void markReady(String storageKey, Integer durationMs, Long sizeBytes) {
        if (isTerminal()) return;
        this.status = RecordingStatus.READY;
        this.storageKey = storageKey;
        this.durationMs = durationMs;
        this.sizeBytes = sizeBytes;
        this.endedAt = Instant.now();
    }

    public void markFailed(String error) {
        if (isTerminal()) return;
        this.status = RecordingStatus.FAILED;
        this.error = error;
        this.endedAt = Instant.now();
    }
}
