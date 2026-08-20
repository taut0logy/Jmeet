package com.taut0logy.jmeet.recording;

import com.taut0logy.jmeet.common.AppException;
import com.taut0logy.jmeet.common.ErrorCode;
import com.taut0logy.jmeet.common.Ids;
import com.taut0logy.jmeet.config.RecordingProperties;
import com.taut0logy.jmeet.job.JobType;
import com.taut0logy.jmeet.meeting.Meeting;
import com.taut0logy.jmeet.meeting.MeetingRepository;
import com.taut0logy.jmeet.meeting.MeetingService;
import com.taut0logy.jmeet.meeting.session.MeetingSession;
import com.taut0logy.jmeet.meeting.session.MeetingSessionRepository;
import com.taut0logy.jmeet.outbox.OutboxService;
import com.taut0logy.jmeet.room.RoomBroadcast;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import tools.jackson.databind.ObjectMapper;

@Service
public class RecordingService {

    private static final Logger log = LoggerFactory.getLogger(RecordingService.class);
    private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(15);
    private static final List<RecordingStatus> ACTIVE_STATUSES = List.of(RecordingStatus.RECORDING,
            RecordingStatus.PROCESSING);

    private final RecordingRepository recordings;
    private final MeetingSessionRepository sessions;
    private final MeetingRepository meetings;
    private final MeetingService meetingService;
    private final EgressPort egress;
    private final RecordingProperties properties;
    private final OutboxService outbox;
    private final ObjectMapper json;
    private final S3Presigner presigner;
    private final StringRedisTemplate redis;
    private final SimpMessagingTemplate messaging;

    public RecordingService(RecordingRepository recordings, MeetingSessionRepository sessions,
            MeetingRepository meetings, MeetingService meetingService, EgressPort egress,
            RecordingProperties properties, OutboxService outbox, ObjectMapper json,
            StringRedisTemplate redis, SimpMessagingTemplate messaging) {
        this.recordings = recordings;
        this.sessions = sessions;
        this.meetings = meetings;
        this.meetingService = meetingService;
        this.egress = egress;
        this.properties = properties;
        this.outbox = outbox;
        this.json = json;
        this.redis = redis;
        this.messaging = messaging;

        Region region = Region.of(properties.region());
        S3Configuration serviceConfig = S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyle())
                .build();
        var presignerBuilder = S3Presigner.builder().region(region).serviceConfiguration(serviceConfig)
                .credentialsProvider(credentialsProvider());
        if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
            presignerBuilder.endpointOverride(URI.create(properties.endpoint()));
        }
        this.presigner = presignerBuilder.build();
    }

    private AwsCredentialsProvider credentialsProvider() {
        if (properties.accessKey() != null && !properties.accessKey().isBlank()) {
            return StaticCredentialsProvider
                    .create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
        }
        return DefaultCredentialsProvider.builder().build();
    }

    @Transactional
    public RecordingResponse start(String userId, String sessionId) {
        MeetingSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Room not found."));
        Meeting meeting = meetings.findById(session.getMeetingId())
                .orElseThrow(() -> new AppException(ErrorCode.MEETING_NOT_FOUND, "Meeting not found."));
        meetingService.requireHostOrCohost(userId, meeting.getId());

        if (!recordings.findByMeetingIdAndStatusIn(meeting.getId(), ACTIVE_STATUSES).isEmpty()) {
            throw new AppException(ErrorCode.RECORDING_ALREADY_ACTIVE,
                    "A recording is already active for this meeting.");
        }

        Recording recording = new Recording(Ids.next(), meeting.getId(), sessionId, userId, "custom");
        String storageKey = "recordings/" + meeting.getId() + "/" + recording.getId() + ".mp4";
        String egressId = egress.startRoomCompositeEgress(meeting.getCode(), storageKey);
        recording.setEgressId(egressId);
        recordings.save(recording);

        broadcastRecordingState(sessionId, true, recording.getStartedAt(), userId);
        return RecordingResponse.from(recording, null);
    }

    @Transactional
    public RecordingResponse stop(String userId, String sessionId) {
        MeetingSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Room not found."));
        Meeting meeting = meetings.findById(session.getMeetingId())
                .orElseThrow(() -> new AppException(ErrorCode.MEETING_NOT_FOUND, "Meeting not found."));
        meetingService.requireHostOrCohost(userId, meeting.getId());

        Recording recording = recordings.findBySessionIdAndStatus(sessionId, RecordingStatus.RECORDING)
                .orElseThrow(
                        () -> new AppException(ErrorCode.RECORDING_NOT_ACTIVE, "No active recording for this room."));

        egress.stopEgress(recording.getEgressId());
        recording.markProcessing();
        broadcastRecordingState(sessionId, false, null, null);
        return RecordingResponse.from(recording, null);
    }

    public List<RecordingResponse> listForMeeting(String userId, String meetingId) {
        meetingService.requireMember(userId, meetingId);
        return recordings.findByMeetingIdOrderByStartedAtDesc(meetingId).stream()
                .map(r -> RecordingResponse.from(r, r.getStatus() == RecordingStatus.READY ? downloadUrl(r) : null))
                .toList();
    }

    /**
     * Stops any recording still active for this session — called when a meeting
     * ends,
     * regardless of whether that happened via a host action or LiveKit's own
     * empty-timeout.
     */
    @Transactional
    public void stopActiveForSession(String sessionId) {
        recordings.findBySessionIdAndStatusIn(sessionId, ACTIVE_STATUSES).forEach(this::stopQuietly);
    }

    /** Jmeet-web shutdown must not leave Egress recording into the void. */
    @PreDestroy
    @Transactional
    public void stopAllActiveOnShutdown() {
        recordings.findByStatusIn(ACTIVE_STATUSES).forEach(this::stopQuietly);
    }

    /** The duration cap. Called by RecordingReconciler. */
    @Transactional
    public void stopIfOverMaxDuration(Recording recording) {
        if (recording.getStatus() != RecordingStatus.RECORDING)
            return;
        if (Instant.now().isAfter(recording.getStartedAt().plus(properties.maxDuration()))) {
            stopQuietly(recording);
        }
    }

    private void stopQuietly(Recording recording) {
        try {
            if (recording.getEgressId() != null)
                egress.stopEgress(recording.getEgressId());
            recording.markProcessing();
            broadcastRecordingState(recording.getSessionId(), false, null, null);
        } catch (Exception e) {
            log.warn("failed to stop egress {} for recording {}: {}", recording.getEgressId(), recording.getId(),
                    e.getMessage());
        }
    }

    private void broadcastRecordingState(String sessionId, boolean active, Instant startedAt, String startedBy) {
        Long rev = redis.opsForValue().increment("room:rev:" + sessionId);
        RoomBroadcast payload = new RoomBroadcast("recording-state", rev == null ? 1 : rev,
                Map.of("active", active, "startedAt", startedAt == null ? "" : startedAt.toString(),
                        "startedBy", startedBy == null ? "" : startedBy));
        try {
            messaging.convertAndSend("/topic/room." + sessionId, payload);
        } catch (MessagingException e) {
            log.warn("failed to broadcast recording state for {}: {}", sessionId, e.getMessage());
        }
    }

    @Transactional
    public void applyEgressStatus(String egressId, EgressStatusSnapshot snapshot) {
        Optional<Recording> maybeRecording = recordings.findByEgressId(egressId);
        if (maybeRecording.isEmpty()) {
            log.warn("received egress status for unknown egress {}", egressId);
            return;
        }
        Recording recording = maybeRecording.get();
        switch (snapshot.status()) {
            case READY -> {
                recording.markReady(snapshot.storageKey(), snapshot.durationMs(), snapshot.sizeBytes());
                notifyReady(recording);
            }
            case FAILED -> recording.markFailed(snapshot.error());
            case RECORDING, PROCESSING ->
                recording.markRecordingOrProcessing(snapshot.status() == RecordingStatus.RECORDING);
        }
    }

    private void notifyReady(Recording recording) {
        Map<String, Object> payload = Map.of("recordingId", recording.getId(), "meetingId", recording.getMeetingId());
        outbox.dispatchJob(JobType.RECORDING_NOTIFY, recording.getId(), json.writeValueAsString(payload));
    }

    private String downloadUrl(Recording recording) {
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(DOWNLOAD_TTL)
                .getObjectRequest(
                        GetObjectRequest.builder().bucket(properties.bucket()).key(recording.getStorageKey()).build())
                .build();
        return presigner.presignGetObject(request).url().toString();
    }
}
