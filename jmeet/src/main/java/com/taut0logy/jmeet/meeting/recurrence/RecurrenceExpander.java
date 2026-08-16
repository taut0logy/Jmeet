package com.taut0logy.jmeet.meeting.recurrence;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.fortuna.ical4j.model.Recur;

/** §9.3: occurrences are computed on read from the rrule; exceptions and overrides are applied on top. */
public final class RecurrenceExpander {

    private static final int MAX_OCCURRENCES = 366;

    private RecurrenceExpander() {
    }

    public static List<OccurrenceView> expand(SeriesDef series, List<MeetingOccurrence> exceptions,
            List<MeetingSeriesOverride> overrides, Instant from, Instant to) {
        if (series.rrule() == null) {
            if (series.startsAt() != null && !series.startsAt().isBefore(from) && !series.startsAt().isAfter(to)) {
                return List.of(new OccurrenceView(series.startsAt(), series.startsAt(), series.durationMin(),
                        series.title(), OccurrenceStatus.SCHEDULED));
            }
            return List.of();
        }

        ZoneId zone = ZoneId.of(series.timezone());
        ZonedDateTime seed = series.startsAt().atZone(zone);
        ZonedDateTime periodStart = from.atZone(zone);
        ZonedDateTime periodEnd = to.atZone(zone);
        if (series.seriesEndsAt() != null && series.seriesEndsAt().isBefore(to)) {
            periodEnd = series.seriesEndsAt().atZone(zone);
        }
        if (!periodEnd.isAfter(periodStart)) {
            return List.of();
        }

        Recur<ZonedDateTime> recur = new Recur<>(series.rrule());
        List<ZonedDateTime> dates = recur.getDates(seed, periodStart, periodEnd, MAX_OCCURRENCES);

        Map<Instant, MeetingOccurrence> exceptionByOriginal = exceptions.stream()
                .collect(Collectors.toMap(MeetingOccurrence::getOriginalStartsAt, e -> e));
        List<MeetingSeriesOverride> sortedOverrides = overrides.stream()
                .sorted(Comparator.comparing(MeetingSeriesOverride::getFromStartsAt))
                .toList();

        List<OccurrenceView> result = new ArrayList<>();
        for (ZonedDateTime date : dates) {
            Instant original = date.toInstant();
            MeetingOccurrence exception = exceptionByOriginal.get(original);
            if (exception != null && exception.getStatus() == OccurrenceStatus.CANCELLED) {
                result.add(new OccurrenceView(original, original, series.durationMin(), series.title(),
                        OccurrenceStatus.CANCELLED));
                continue;
            }
            if (exception != null && exception.getStatus() == OccurrenceStatus.MOVED) {
                result.add(new OccurrenceView(original, exception.getStartsAt(),
                        exception.getDurationMin() != null ? exception.getDurationMin() : series.durationMin(),
                        exception.getTitle() != null ? exception.getTitle() : series.title(),
                        OccurrenceStatus.MOVED));
                continue;
            }

            String title = series.title();
            int durationMin = series.durationMin();
            String startTimeLocal = null;
            for (MeetingSeriesOverride override : sortedOverrides) {
                if (override.getFromStartsAt().isAfter(original)) break;
                if (override.getTitle() != null) title = override.getTitle();
                if (override.getDurationMin() != null) durationMin = override.getDurationMin();
                if (override.getStartTimeLocal() != null) startTimeLocal = override.getStartTimeLocal();
            }

            Instant occurrenceStart = original;
            if (startTimeLocal != null) {
                occurrenceStart = date.toLocalDate().atTime(LocalTime.parse(startTimeLocal)).atZone(zone).toInstant();
            }
            result.add(new OccurrenceView(original, occurrenceStart, durationMin, title, OccurrenceStatus.SCHEDULED));
        }
        return result;
    }
}
