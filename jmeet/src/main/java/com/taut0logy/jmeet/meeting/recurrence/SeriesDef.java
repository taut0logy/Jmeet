package com.taut0logy.jmeet.meeting.recurrence;

import java.time.Instant;

public record SeriesDef(String rrule, Instant startsAt, int durationMin, String title, String timezone,
        Instant seriesEndsAt) {
}
