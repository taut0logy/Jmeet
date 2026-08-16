package com.taut0logy.jmeet.meeting.recurrence;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Exception row for a single occurrence — cancelled or moved. Ordinary occurrences are never stored. */
@Entity
@Table(name = "meeting_occurrence")
public class MeetingOccurrence {

    @Id
    private String id;

    private String meetingId;
    private Instant originalStartsAt;

    @Enumerated(EnumType.STRING)
    private OccurrenceStatus status;

    private Instant startsAt;
    private Integer durationMin;
    private String title;

    protected MeetingOccurrence() {
    }

    public MeetingOccurrence(String id, String meetingId, Instant originalStartsAt, OccurrenceStatus status) {
        this.id = id;
        this.meetingId = meetingId;
        this.originalStartsAt = originalStartsAt;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public String getMeetingId() {
        return meetingId;
    }

    public Instant getOriginalStartsAt() {
        return originalStartsAt;
    }

    public OccurrenceStatus getStatus() {
        return status;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Integer getDurationMin() {
        return durationMin;
    }

    public String getTitle() {
        return title;
    }

    public void cancel() {
        this.status = OccurrenceStatus.CANCELLED;
    }

    public void move(Instant startsAt, Integer durationMin, String title) {
        this.status = OccurrenceStatus.MOVED;
        this.startsAt = startsAt;
        this.durationMin = durationMin;
        this.title = title;
    }
}
