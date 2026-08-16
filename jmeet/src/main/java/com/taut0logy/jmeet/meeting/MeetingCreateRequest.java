package com.taut0logy.jmeet.meeting;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record MeetingCreateRequest(@NotBlank String title, String description, @NotNull MeetingKind kind,
        Instant startsAt, Integer durationMin, String timezone, String rrule, Instant seriesEndsAt,
        MeetingAccess access, WaitingRoomPolicy waitingRoom, Boolean allowGuests, Boolean muteOnEntry,
        Boolean cameraOffOnEntry) {
}
