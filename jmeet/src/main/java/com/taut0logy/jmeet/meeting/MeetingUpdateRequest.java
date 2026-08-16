package com.taut0logy.jmeet.meeting;

import java.time.Instant;

public record MeetingUpdateRequest(String title, String description, Instant startsAt, Integer durationMin,
        String timezone, String rrule, Instant seriesEndsAt, MeetingAccess access, WaitingRoomPolicy waitingRoom,
        Boolean allowGuests, Boolean muteOnEntry, Boolean cameraOffOnEntry, String startTimeLocal) {
}
