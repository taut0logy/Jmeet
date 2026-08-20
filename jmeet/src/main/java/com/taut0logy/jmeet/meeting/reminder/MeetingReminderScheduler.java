package com.taut0logy.jmeet.meeting.reminder;

import com.taut0logy.jmeet.config.RoomProperties;
import com.taut0logy.jmeet.job.JobType;
import com.taut0logy.jmeet.meeting.Meeting;
import com.taut0logy.jmeet.meeting.MeetingKind;
import com.taut0logy.jmeet.meeting.MeetingRepository;
import com.taut0logy.jmeet.meeting.MeetingService;
import com.taut0logy.jmeet.meeting.MeetingStatus;
import com.taut0logy.jmeet.meeting.recurrence.OccurrenceStatus;
import com.taut0logy.jmeet.meeting.recurrence.OccurrenceView;
import com.taut0logy.jmeet.outbox.OutboxService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Meeting.reminder pipeline, first hop: find occurrences starting soon, dedup,
 * and hand
 * off the per-recipient fan-out to occurrence.expand rather than doing it
 * inline, keeps this
 * scan cheap regardless of how many members a meeting has.
 */
@Component
public class MeetingReminderScheduler {

    private static final Duration DEDUP_TTL = Duration.ofHours(24);

    private final MeetingRepository meetings;
    private final MeetingService meetingService;
    private final RoomProperties properties;
    private final StringRedisTemplate redis;
    private final OutboxService outbox;
    private final ObjectMapper json;
    private final Clock clock;

    public MeetingReminderScheduler(MeetingRepository meetings, MeetingService meetingService,
            RoomProperties properties,
            StringRedisTemplate redis, OutboxService outbox, ObjectMapper json, Clock clock) {
        this.meetings = meetings;
        this.meetingService = meetingService;
        this.properties = properties;
        this.redis = redis;
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "meeting-reminder-scheduler", lockAtMostFor = "5m")
    public void scan() {
        Instant now = Instant.now(clock);
        Instant horizon = now.plus(properties.reminderLeadTime());

        for (Meeting meeting : meetings.findByKindAndStatus(MeetingKind.SCHEDULED, MeetingStatus.SCHEDULED)) {
            if (meeting.getStartsAt() == null)
                continue;
            for (OccurrenceView occurrence : meetingService.occurrencesInRange(meeting, now, horizon)) {
                if (occurrence.status() == OccurrenceStatus.CANCELLED)
                    continue;
                maybeDispatch(meeting, occurrence.startsAt());
            }
        }
    }

    private void maybeDispatch(Meeting meeting, Instant occurrenceStartsAt) {
        String key = "reminder:dispatched:" + meeting.getId() + ":" + occurrenceStartsAt.getEpochSecond();
        Boolean first = redis.opsForValue().setIfAbsent(key, "1", DEDUP_TTL);
        if (first == null || !first)
            return;

        OccurrenceExpandPayload payload = new OccurrenceExpandPayload(meeting.getId(), occurrenceStartsAt);
        outbox.dispatchJob(JobType.OCCURRENCE_EXPAND, meeting.getId(), json.writeValueAsString(payload));
    }
}
