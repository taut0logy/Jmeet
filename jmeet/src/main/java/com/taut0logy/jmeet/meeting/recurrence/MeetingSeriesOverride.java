package com.taut0logy.jmeet.meeting.recurrence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** "This and following" edit as a range override. No rrule field: a pattern change requires scope=all. */
@Entity
@Table(name = "meeting_series_override")
public class MeetingSeriesOverride {

    @Id
    private String id;

    private String meetingId;
    private Instant fromStartsAt;
    private String title;
    private Integer durationMin;
    private String startTimeLocal;

    protected MeetingSeriesOverride() {
    }

    public MeetingSeriesOverride(String id, String meetingId, Instant fromStartsAt, String title,
            Integer durationMin, String startTimeLocal) {
        this.id = id;
        this.meetingId = meetingId;
        this.fromStartsAt = fromStartsAt;
        this.title = title;
        this.durationMin = durationMin;
        this.startTimeLocal = startTimeLocal;
    }

    public String getId() {
        return id;
    }

    public String getMeetingId() {
        return meetingId;
    }

    public Instant getFromStartsAt() {
        return fromStartsAt;
    }

    public String getTitle() {
        return title;
    }

    public Integer getDurationMin() {
        return durationMin;
    }

    public String getStartTimeLocal() {
        return startTimeLocal;
    }
}
