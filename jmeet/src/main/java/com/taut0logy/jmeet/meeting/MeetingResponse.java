package com.taut0logy.jmeet.meeting;

import com.taut0logy.jmeet.meeting.member.MemberResponse;
import com.taut0logy.jmeet.meeting.recurrence.OccurrenceView;
import java.time.Instant;
import java.util.List;

public record MeetingResponse(String id, String code, String title, String description, String ownerId,
        MeetingKind kind, MeetingStatus status, Instant startsAt, Integer durationMin, String timezone,
        String rrule, Instant seriesEndsAt, MeetingAccess access, WaitingRoomPolicy waitingRoom,
        boolean allowGuests, boolean muteOnEntry, boolean cameraOffOnEntry, Instant lockedAt,
        List<MemberResponse> members, List<OccurrenceView> occurrences) {

    public static MeetingResponse from(Meeting meeting, List<MemberResponse> members, List<OccurrenceView> occurrences) {
        return new MeetingResponse(meeting.getId(), meeting.getCode(), meeting.getTitle(), meeting.getDescription(),
                meeting.getOwnerId(), meeting.getKind(), meeting.getStatus(), meeting.getStartsAt(),
                meeting.getDurationMin(), meeting.getTimezone(), meeting.getRrule(), meeting.getSeriesEndsAt(),
                meeting.getAccess(), meeting.getWaitingRoom(), meeting.isAllowGuests(), meeting.isMuteOnEntry(),
                meeting.isCameraOffOnEntry(), meeting.getLockedAt(), members, occurrences);
    }
}
