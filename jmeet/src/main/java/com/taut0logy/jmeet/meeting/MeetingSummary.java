package com.taut0logy.jmeet.meeting;

import java.time.Instant;

public record MeetingSummary(String id, String code, String title, MeetingKind kind, MeetingStatus status,
        Instant startsAt, Integer durationMin, String ownerId, MeetingAccess access) {

    public static MeetingSummary from(Meeting meeting) {
        return new MeetingSummary(meeting.getId(), meeting.getCode(), meeting.getTitle(), meeting.getKind(),
                meeting.getStatus(), meeting.getStartsAt(), meeting.getDurationMin(), meeting.getOwnerId(),
                meeting.getAccess());
    }
}
