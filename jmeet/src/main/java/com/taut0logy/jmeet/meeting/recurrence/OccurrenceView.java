package com.taut0logy.jmeet.meeting.recurrence;

import java.time.Instant;

public record OccurrenceView(Instant originalStartsAt, Instant startsAt, int durationMin, String title,
        OccurrenceStatus status) {
}
