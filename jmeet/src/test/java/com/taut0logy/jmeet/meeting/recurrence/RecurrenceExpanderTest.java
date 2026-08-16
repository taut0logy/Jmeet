package com.taut0logy.jmeet.meeting.recurrence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecurrenceExpanderTest {

    @Test
    void nonRecurringReturnsSingleOccurrenceWithinRange() {
        Instant startsAt = Instant.parse("2026-03-10T14:00:00Z");
        SeriesDef series = new SeriesDef(null, startsAt, 60, "Standup", "UTC", null);

        List<OccurrenceView> inRange = RecurrenceExpander.expand(series, List.of(), List.of(),
                Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-04-01T00:00:00Z"));
        assertThat(inRange).hasSize(1);
        assertThat(inRange.get(0).startsAt()).isEqualTo(startsAt);

        List<OccurrenceView> outOfRange = RecurrenceExpander.expand(series, List.of(), List.of(),
                Instant.parse("2026-04-01T00:00:00Z"), Instant.parse("2026-05-01T00:00:00Z"));
        assertThat(outOfRange).isEmpty();
    }

    @Test
    void weeklyExpansionCrossesDstBoundaryKeepingLocalWallClockTime() {
        ZoneId zone = ZoneId.of("America/New_York");
        Instant seed = ZonedDateTime.of(2026, 3, 1, 9, 0, 0, 0, zone).toInstant();
        SeriesDef series = new SeriesDef("FREQ=WEEKLY;BYDAY=SU", seed, 60, "Weekly Sync", zone.getId(), null);

        List<OccurrenceView> occurrences = RecurrenceExpander.expand(series, List.of(), List.of(),
                Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-03-31T00:00:00Z"));

        assertThat(occurrences).hasSize(5);
        // Mar 1 is EST (UTC-5): 09:00 local = 14:00 UTC.
        assertThat(occurrences.get(0).startsAt()).isEqualTo(Instant.parse("2026-03-01T14:00:00Z"));
        // US DST begins Mar 8 2026 at 02:00 local, before this occurrence: it lands in EDT (UTC-4).
        assertThat(occurrences.get(1).startsAt()).isEqualTo(Instant.parse("2026-03-08T13:00:00Z"));
        for (OccurrenceView occurrence : occurrences) {
            assertThat(ZonedDateTime.ofInstant(occurrence.startsAt(), zone).getHour()).isEqualTo(9);
        }
    }

    @Test
    void seriesOverridesStackAndLatestWinsPerField() {
        Instant seed = Instant.parse("2026-01-05T09:00:00Z");
        SeriesDef series = new SeriesDef("FREQ=WEEKLY;BYDAY=MO", seed, 60, "Original", "UTC", null);

        MeetingSeriesOverride first = new MeetingSeriesOverride("o1", "m1", Instant.parse("2026-01-10T00:00:00Z"),
                "Renamed", 30, null);
        MeetingSeriesOverride second = new MeetingSeriesOverride("o2", "m1", Instant.parse("2026-01-17T00:00:00Z"),
                null, 45, null);

        List<OccurrenceView> occurrences = RecurrenceExpander.expand(series, List.of(), List.of(first, second),
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-02-01T00:00:00Z"));

        OccurrenceView jan5 = occurrences.get(0);
        assertThat(jan5.title()).isEqualTo("Original");
        assertThat(jan5.durationMin()).isEqualTo(60);

        OccurrenceView jan12 = occurrences.get(1);
        assertThat(jan12.title()).isEqualTo("Renamed");
        assertThat(jan12.durationMin()).isEqualTo(30);

        OccurrenceView jan19 = occurrences.get(2);
        assertThat(jan19.title()).isEqualTo("Renamed");
        assertThat(jan19.durationMin()).isEqualTo(45);
    }

    @Test
    void seriesOverrideShiftsLocalStartTime() {
        ZoneId zone = ZoneId.of("UTC");
        Instant seed = Instant.parse("2026-01-05T09:00:00Z");
        SeriesDef series = new SeriesDef("FREQ=WEEKLY;BYDAY=MO", seed, 60, "Standup", zone.getId(), null);
        MeetingSeriesOverride override = new MeetingSeriesOverride("o1", "m1", Instant.parse("2026-01-10T00:00:00Z"),
                null, null, "10:30");

        List<OccurrenceView> occurrences = RecurrenceExpander.expand(series, List.of(), List.of(override),
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-20T00:00:00Z"));

        assertThat(occurrences.get(0).startsAt()).isEqualTo(Instant.parse("2026-01-05T09:00:00Z"));
        assertThat(occurrences.get(1).startsAt()).isEqualTo(Instant.parse("2026-01-12T10:30:00Z"));
    }

    @Test
    void occurrenceExceptionsOverrideCancelledAndMoved() {
        Instant seed = Instant.parse("2026-01-05T09:00:00Z");
        SeriesDef series = new SeriesDef("FREQ=WEEKLY;BYDAY=MO", seed, 60, "Standup", "UTC", null);

        MeetingOccurrence cancelled = new MeetingOccurrence("e1", "m1", Instant.parse("2026-01-12T09:00:00Z"),
                OccurrenceStatus.SCHEDULED);
        cancelled.cancel();

        MeetingOccurrence moved = new MeetingOccurrence("e2", "m1", Instant.parse("2026-01-19T09:00:00Z"),
                OccurrenceStatus.SCHEDULED);
        moved.move(Instant.parse("2026-01-20T15:00:00Z"), 90, "Moved standup");

        List<OccurrenceView> occurrences = RecurrenceExpander.expand(series, List.of(cancelled, moved), List.of(),
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-26T00:00:00Z"));

        assertThat(occurrences).hasSize(3);
        assertThat(occurrences.get(1).status()).isEqualTo(OccurrenceStatus.CANCELLED);
        assertThat(occurrences.get(2).status()).isEqualTo(OccurrenceStatus.MOVED);
        assertThat(occurrences.get(2).startsAt()).isEqualTo(Instant.parse("2026-01-20T15:00:00Z"));
        assertThat(occurrences.get(2).durationMin()).isEqualTo(90);
        assertThat(occurrences.get(2).title()).isEqualTo("Moved standup");
    }
}
