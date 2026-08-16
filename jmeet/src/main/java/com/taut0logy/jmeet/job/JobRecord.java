package com.taut0logy.jmeet.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "job_record")
public class JobRecord {

    @Id
    private UUID messageId;

    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    private JobRecordStatus status;

    private int attempts;

    private String lastError;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected JobRecord() {
    }

    public JobRecord(UUID messageId, String type, String payload) {
        this.messageId = messageId;
        this.type = type;
        this.payload = payload;
        this.status = JobRecordStatus.RUNNING;
        this.attempts = 0;
        Instant now = Instant.now();
        this.startedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public String getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public JobRecordStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void succeed() {
        this.status = JobRecordStatus.SUCCEEDED;
        this.finishedAt = Instant.now();
        this.updatedAt = this.finishedAt;
    }

    public void retry(int attempts, String error) {
        this.status = JobRecordStatus.RETRYING;
        this.attempts = attempts;
        this.lastError = error;
        this.updatedAt = Instant.now();
    }

    public void die(int attempts, String error) {
        this.status = JobRecordStatus.DEAD;
        this.attempts = attempts;
        this.lastError = error;
        this.finishedAt = Instant.now();
        this.updatedAt = this.finishedAt;
    }
}
