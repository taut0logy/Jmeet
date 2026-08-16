package com.taut0logy.jmeet.room;

import com.taut0logy.jmeet.config.RoomProperties;
import com.taut0logy.jmeet.job.JobType;
import com.taut0logy.jmeet.meeting.Meeting;
import com.taut0logy.jmeet.meeting.MeetingKind;
import com.taut0logy.jmeet.meeting.MeetingRepository;
import com.taut0logy.jmeet.meeting.session.MeetingSession;
import com.taut0logy.jmeet.meeting.session.MeetingSessionRepository;
import com.taut0logy.jmeet.outbox.OutboxService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** §9.4: the duration warning and scheduled auto-end, both driven off the same scan since they
 * share the same "how close to the scheduled end are we" question. */
@Component
public class MeetingDurationScheduler {

    private static final Duration DEDUP_TTL = Duration.ofHours(6);

    private final MeetingSessionRepository sessions;
    private final MeetingRepository meetings;
    private final RoomProperties properties;
    private final RoomService roomService;
    private final StringRedisTemplate redis;
    private final OutboxService outbox;
    private final ObjectMapper json;
    private final Clock clock;

    public MeetingDurationScheduler(MeetingSessionRepository sessions, MeetingRepository meetings, RoomProperties properties,
            RoomService roomService, StringRedisTemplate redis, OutboxService outbox, ObjectMapper json, Clock clock) {
        this.sessions = sessions;
        this.meetings = meetings;
        this.properties = properties;
        this.roomService = roomService;
        this.redis = redis;
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 30_000)
    @SchedulerLock(name = "meeting-duration-scheduler", lockAtMostFor = "5m")
    public void scan() {
        Instant now = Instant.now(clock);
        for (MeetingSession session : sessions.findByEndedAtIsNull()) {
            Meeting meeting = meetings.findById(session.getMeetingId()).orElse(null);
            if (meeting == null || meeting.getKind() != MeetingKind.SCHEDULED
                    || meeting.getStartsAt() == null || meeting.getDurationMin() == null) {
                continue;
            }

            Instant scheduledEnd = meeting.getStartsAt().plus(Duration.ofMinutes(meeting.getDurationMin()));
            Instant warnAt = scheduledEnd.minus(properties.durationWarning());
            if (!now.isBefore(warnAt) && now.isBefore(scheduledEnd)) {
                maybeWarn(session.getId());
            }

            Instant autoEndAt = scheduledEnd.plus(properties.autoEndGrace());
            if (!now.isBefore(autoEndAt)) {
                maybeDispatchClose(session.getId());
            }
        }
    }

    private void maybeWarn(String sessionId) {
        if (!claim("duration-warning:sent:" + sessionId)) return;
        roomService.broadcastDurationWarning(sessionId);
    }

    private void maybeDispatchClose(String sessionId) {
        if (!claim("session-close:dispatched:" + sessionId)) return;
        outbox.dispatchJob(JobType.SESSION_CLOSE, sessionId, json.writeValueAsString(new SessionClosePayload(sessionId)));
    }

    private boolean claim(String key) {
        Boolean first = redis.opsForValue().setIfAbsent(key, "1", DEDUP_TTL);
        return first != null && first;
    }
}
