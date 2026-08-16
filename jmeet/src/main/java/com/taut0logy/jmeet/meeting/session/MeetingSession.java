package com.taut0logy.jmeet.meeting.session;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "meeting_session")
public class MeetingSession {

    @Id
    private String id;

    private String meetingId;
    private Instant occurrenceStartsAt;
    private Instant startedAt;
    private Instant endedAt;
    private int peakParticipants;

    protected MeetingSession() {
    }

    public MeetingSession(String id, String meetingId, Instant occurrenceStartsAt) {
        this.id = id;
        this.meetingId = meetingId;
        this.occurrenceStartsAt = occurrenceStartsAt;
        this.startedAt = Instant.now();
        this.peakParticipants = 0;
    }

    public String getId() {
        return id;
    }

    public String getMeetingId() {
        return meetingId;
    }

    public Instant getOccurrenceStartsAt() {
        return occurrenceStartsAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public int getPeakParticipants() {
        return peakParticipants;
    }

    public boolean isLive() {
        return endedAt == null;
    }

    public void end() {
        if (endedAt == null) this.endedAt = Instant.now();
    }

    public void recordPeak(int currentParticipants) {
        if (currentParticipants > peakParticipants) this.peakParticipants = currentParticipants;
    }
}
