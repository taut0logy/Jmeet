package com.taut0logy.jmeet.room;

import com.taut0logy.jmeet.auth.GuestJoinToken;
import com.taut0logy.jmeet.auth.GuestJoinTokenService;
import com.taut0logy.jmeet.common.AppException;
import com.taut0logy.jmeet.common.ErrorCode;
import com.taut0logy.jmeet.common.Ids;
import com.taut0logy.jmeet.config.RoomProperties;
import com.taut0logy.jmeet.meeting.Meeting;
import com.taut0logy.jmeet.meeting.MeetingRepository;
import com.taut0logy.jmeet.meeting.MeetingService;
import com.taut0logy.jmeet.meeting.MeetingStatus;
import com.taut0logy.jmeet.meeting.ParticipantRole;
import com.taut0logy.jmeet.meeting.session.ChatMessage;
import com.taut0logy.jmeet.meeting.session.ChatMessageRepository;
import com.taut0logy.jmeet.meeting.session.MeetingSession;
import com.taut0logy.jmeet.meeting.session.MeetingSessionRepository;
import com.taut0logy.jmeet.meeting.session.Participation;
import com.taut0logy.jmeet.meeting.session.ParticipationRepository;
import com.taut0logy.jmeet.user.Profile;
import com.taut0logy.jmeet.user.ProfileRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomService.class);

    private final MeetingRepository meetings;
    private final MeetingService meetingService;
    private final MeetingSessionRepository sessions;
    private final ParticipationRepository participations;
    private final ChatMessageRepository chatMessages;
    private final ProfileRepository profiles;
    private final GuestJoinTokenService joinTokens;
    private final RoomMediaPort media;
    private final RoomProperties roomProperties;
    private final StringRedisTemplate redis;
    private final SimpMessagingTemplate messaging;
    private final ObjectMapper json;

    public RoomService(MeetingRepository meetings, MeetingService meetingService, MeetingSessionRepository sessions,
            ParticipationRepository participations, ChatMessageRepository chatMessages, ProfileRepository profiles,
            GuestJoinTokenService joinTokens, RoomMediaPort media, RoomProperties roomProperties,
            StringRedisTemplate redis, SimpMessagingTemplate messaging, ObjectMapper json) {
        this.meetings = meetings;
        this.meetingService = meetingService;
        this.sessions = sessions;
        this.participations = participations;
        this.chatMessages = chatMessages;
        this.profiles = profiles;
        this.joinTokens = joinTokens;
        this.media = media;
        this.roomProperties = roomProperties;
        this.redis = redis;
        this.messaging = messaging;
        this.json = json;
    }

    @Transactional
    public RoomJoinResponse join(String code, String joinToken) {
        GuestJoinToken claims = joinTokens.verify(joinToken);
        if (!claims.meetingCode().equals(code)) {
            throw new AppException(ErrorCode.JOIN_TOKEN_INVALID, "This join link is invalid.");
        }

        Meeting meeting = meetings.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.MEETING_NOT_FOUND, "Meeting not found."));
        if (meeting.getStatus() == MeetingStatus.CANCELLED || meeting.getStatus() == MeetingStatus.ENDED) {
            throw new AppException(ErrorCode.MEETING_NOT_JOINABLE, "This meeting is no longer joinable.");
        }
        ParticipantRole role = ParticipantRole.valueOf(claims.role());
        boolean hostOrCohost = role == ParticipantRole.HOST || role == ParticipantRole.COHOST;
        if (meeting.getLockedAt() != null && !hostOrCohost) {
            throw new AppException(ErrorCode.MEETING_LOCKED, "This meeting is locked.");
        }

        String userId = claims.userId();
        if (userId != null && roomProperties.singleMeetingEnabled()
                && !participations.findByUserIdAndLeftAtIsNull(userId).isEmpty()) {
            throw new AppException(ErrorCode.ALREADY_IN_MEETING, "You are already in another meeting.");
        }

        MeetingSession session = findOrCreateLiveSession(meeting);
        long activeCount = participations.findBySessionIdAndLeftAtIsNull(session.getId()).size();
        if (activeCount >= roomProperties.maxParticipants()) {
            throw new AppException(ErrorCode.ROOM_FULL, "This meeting is full.");
        }

        String peerId = Ids.next();
        String avatarUrl = userId != null ? profiles.findById(userId).map(Profile::getAvatarUrl).orElse(null) : null;

        if (claims.requiresApproval()) {
            PendingAdmission pending = new PendingAdmission(peerId, claims.displayName(), role.name(), userId, claims.guestId());
            redis.opsForHash().put(pendingKey(session.getId()), peerId, json.writeValueAsString(pending));
            long rev = bumpRev(session.getId());
            broadcast(roomTopic(session.getId()),
                    new RoomBroadcast("admission-requested", rev, Map.of("peerId", peerId, "displayName", claims.displayName())));
            return new RoomJoinResponse("PENDING", peerId, null, snapshot(session, meeting, rev));
        }

        Participation participation = new Participation(Ids.next(), session.getId(), peerId, userId, claims.guestId(),
                claims.displayName(), role, null);
        participations.save(participation);
        session.recordPeak((int) activeCount + 1);

        String token = media.mintToken(meeting.getCode(), peerId, claims.displayName(), role,
                new TokenMetadata(role.name(), userId, claims.guestId(), avatarUrl));

        long rev = bumpRev(session.getId());
        broadcastPresence(session.getId(), "joined", participation, rev);
        return new RoomJoinResponse("ADMITTED", peerId, token, snapshot(session, meeting, rev));
    }

    public RoomSnapshot sync(String sessionId, String peerId) {
        MeetingSession session = requireSession(sessionId);
        requireActiveParticipant(sessionId, peerId);
        Meeting meeting = requireMeeting(session);
        return snapshot(session, meeting, currentRev(sessionId));
    }

    @Transactional
    public RoomSnapshot admit(String userId, String sessionId, AdmissionRequest request) {
        MeetingSession session = requireSession(sessionId);
        Meeting meeting = requireMeeting(session);
        meetingService.requireHostOrCohost(userId, meeting.getId());

        if (Boolean.TRUE.equals(request.admitAll())) {
            Set<Object> peerIds = redis.opsForHash().keys(pendingKey(sessionId));
            for (Object peerId : peerIds) {
                admitOne(session, meeting, (String) peerId, userId);
            }
        } else if (request.peerId() != null) {
            if (Boolean.FALSE.equals(request.approve())) {
                denyOne(session, request.peerId());
            } else {
                admitOne(session, meeting, request.peerId(), userId);
            }
        }
        return snapshot(session, meeting, currentRev(sessionId));
    }

    @Transactional
    public void mute(String userId, String sessionId, String peerId, boolean mute) {
        MeetingSession session = requireSession(sessionId);
        Meeting meeting = requireMeeting(session);
        meetingService.requireHostOrCohost(userId, meeting.getId());
        Participation participation = requireActiveParticipation(sessionId, peerId);

        media.listParticipants(meeting.getCode()).stream()
                .filter(p -> p.identity().equals(peerId))
                .findFirst()
                .ifPresent(p -> p.tracks().stream()
                        .filter(t -> "MICROPHONE".equals(t.source()))
                        .forEach(t -> media.muteTrack(meeting.getCode(), peerId, t.sid(), mute)));

        long rev = bumpRev(sessionId);
        broadcast(roomTopic(sessionId),
                new RoomBroadcast("mute", rev, Map.of("peerId", participation.getPeerId(), "muted", mute)));
    }

    @Transactional
    public RoleChangeResponse changeRole(String userId, String sessionId, String peerId, ParticipantRole newRole) {
        MeetingSession session = requireSession(sessionId);
        Meeting meeting = requireMeeting(session);
        meetingService.requireHostOrCohost(userId, meeting.getId());
        Participation participation = requireActiveParticipation(sessionId, peerId);

        participation.setRole(newRole);
        TokenMetadata metadata = new TokenMetadata(newRole.name(), participation.getUserId(), participation.getGuestId(),
                participation.getUserId() != null
                        ? profiles.findById(participation.getUserId()).map(Profile::getAvatarUrl).orElse(null)
                        : null);
        media.updateParticipantMetadata(meeting.getCode(), peerId, metadata);
        String token = media.mintToken(meeting.getCode(), peerId, participation.getDisplayName(), newRole, metadata);

        long rev = bumpRev(sessionId);
        broadcast(roomTopic(sessionId),
                new RoomBroadcast("role-changed", rev, Map.of("peerId", peerId, "role", newRole.name())));
        return new RoleChangeResponse(token);
    }

    @Transactional
    public void removeParticipant(String userId, String sessionId, String peerId) {
        MeetingSession session = requireSession(sessionId);
        Meeting meeting = requireMeeting(session);
        meetingService.requireHostOrCohost(userId, meeting.getId());
        Participation participation = requireActiveParticipation(sessionId, peerId);

        media.removeParticipant(meeting.getCode(), peerId);
        participation.leave();

        long rev = bumpRev(sessionId);
        broadcastPresence(sessionId, "left", participation, rev);
    }

    @Transactional
    public RoomSnapshot flags(String userId, String sessionId, RoomFlagsRequest request) {
        MeetingSession session = requireSession(sessionId);
        Meeting meeting = requireMeeting(session);
        meetingService.requireHostOrCohost(userId, meeting.getId());

        if (request.locked() != null) {
            if (request.locked()) meeting.lock(); else meeting.unlock();
        }
        if (request.screenShareEnabled() != null) {
            redis.opsForHash().put(flagsKey(sessionId), "screenShareEnabled", request.screenShareEnabled().toString());
        }
        if (Boolean.TRUE.equals(request.muteAll())) {
            String actorPeerId = participations.findBySessionIdAndLeftAtIsNull(sessionId).stream()
                    .filter(p -> userId.equals(p.getUserId()))
                    .map(Participation::getPeerId)
                    .findFirst().orElse(null);
            media.listParticipants(meeting.getCode()).stream()
                    .filter(p -> !p.identity().equals(actorPeerId))
                    .forEach(p -> p.tracks().stream()
                            .filter(t -> "MICROPHONE".equals(t.source()))
                            .forEach(t -> media.muteTrack(meeting.getCode(), p.identity(), t.sid(), true)));
        }

        long rev = bumpRev(sessionId);
        RoomSnapshot snapshot = snapshot(session, meeting, rev);
        broadcast(roomTopic(sessionId), new RoomBroadcast("flags-changed", rev,
                Map.of("locked", meeting.getLockedAt() != null, "screenShareEnabled", snapshot.screenShareEnabled())));
        return snapshot;
    }

    @Transactional
    public void endForAll(String userId, String sessionId) {
        MeetingSession session = requireSession(sessionId);
        Meeting meeting = requireMeeting(session);
        meetingService.requireHostOrCohost(userId, meeting.getId());

        media.deleteRoom(meeting.getCode());
        participations.findBySessionIdAndLeftAtIsNull(sessionId).forEach(Participation::leave);
        session.end();

        long rev = bumpRev(sessionId);
        broadcast(roomTopic(sessionId), new RoomBroadcast("room-ended", rev, Map.of()));
    }

    @Transactional
    public void sendChat(String sessionId, String peerId, String body) {
        Participation participation = requireActiveParticipation(sessionId, peerId);
        ChatMessage message = new ChatMessage(Ids.next(), sessionId, peerId, participation.getUserId(),
                participation.getDisplayName(), body);
        chatMessages.save(message);

        long rev = bumpRev(sessionId);
        broadcast(roomTopic(sessionId), new RoomBroadcast("chat", rev, ChatMessageView.from(message)));
    }

    public void raiseHand(String sessionId, String peerId, boolean raised) {
        requireActiveParticipation(sessionId, peerId);
        if (raised) {
            redis.opsForSet().add(handsKey(sessionId), peerId);
        } else {
            redis.opsForSet().remove(handsKey(sessionId), peerId);
        }
        long rev = bumpRev(sessionId);
        broadcast(roomTopic(sessionId),
                new RoomBroadcast("hand-raised", rev, Map.of("peerId", peerId, "raised", raised)));
    }

    @Transactional
    public void handleWebhook(RoomWebhookEvent event) {
        switch (event.type()) {
            case "room_finished" -> onRoomFinished(event.roomName());
            case "participant_left" -> onParticipantLeft(event.roomName(), event.participantIdentity());
            default -> { /* room_started, participant_joined, track_*, egress_* — informational only in M4 */ }
        }
    }

    private void onRoomFinished(String roomCode) {
        if (roomCode == null) return;
        meetings.findByCode(roomCode)
                .flatMap(meeting -> sessions.findByMeetingIdAndEndedAtIsNull(meeting.getId()))
                .ifPresent(session -> {
                    participations.findBySessionIdAndLeftAtIsNull(session.getId()).forEach(Participation::leave);
                    session.end();
                });
    }

    private void onParticipantLeft(String roomCode, String identity) {
        if (roomCode == null || identity == null) return;
        meetings.findByCode(roomCode)
                .flatMap(meeting -> sessions.findByMeetingIdAndEndedAtIsNull(meeting.getId()))
                .flatMap(session -> participations.findBySessionIdAndPeerId(session.getId(), identity))
                .filter(Participation::isActive)
                .ifPresent(participation -> {
                    participation.leave();
                    long rev = bumpRev(participation.getSessionId());
                    broadcastPresence(participation.getSessionId(), "left", participation, rev);
                });
    }

    private void admitOne(MeetingSession session, Meeting meeting, String peerId, String admittedBy) {
        Object raw = redis.opsForHash().get(pendingKey(session.getId()), peerId);
        if (raw == null) return;
        PendingAdmission pending = json.readValue((String) raw, PendingAdmission.class);
        redis.opsForHash().delete(pendingKey(session.getId()), peerId);

        ParticipantRole role = ParticipantRole.valueOf(pending.role());
        Participation participation = new Participation(Ids.next(), session.getId(), peerId, pending.userId(),
                pending.guestId(), pending.displayName(), role, admittedBy);
        participations.save(participation);

        String avatarUrl = pending.userId() != null
                ? profiles.findById(pending.userId()).map(Profile::getAvatarUrl).orElse(null) : null;
        String token = media.mintToken(meeting.getCode(), peerId, pending.displayName(), role,
                new TokenMetadata(role.name(), pending.userId(), pending.guestId(), avatarUrl));

        long rev = bumpRev(session.getId());
        broadcast(peerTopic(session.getId(), peerId),
                new RoomBroadcast("admission-decided", rev, Map.of("status", "ADMITTED", "token", token)));
        broadcastPresence(session.getId(), "joined", participation, rev);
    }

    private void denyOne(MeetingSession session, String peerId) {
        redis.opsForHash().delete(pendingKey(session.getId()), peerId);
        long rev = bumpRev(session.getId());
        broadcast(peerTopic(session.getId(), peerId),
                new RoomBroadcast("admission-decided", rev, Map.of("status", "DENIED")));
    }

    private MeetingSession findOrCreateLiveSession(Meeting meeting) {
        return sessions.findByMeetingIdAndEndedAtIsNull(meeting.getId()).orElseGet(() -> {
            try {
                return sessions.save(new MeetingSession(Ids.next(), meeting.getId(), meeting.getStartsAt()));
            } catch (DataIntegrityViolationException e) {
                return sessions.findByMeetingIdAndEndedAtIsNull(meeting.getId())
                        .orElseThrow(() -> new AppException(ErrorCode.INTERNAL, "failed to establish a live session"));
            }
        });
    }

    private RoomSnapshot snapshot(MeetingSession session, Meeting meeting, long rev) {
        List<Participation> active = participations.findBySessionIdAndLeftAtIsNull(session.getId());
        Set<String> raisedHands = redis.opsForSet().members(handsKey(session.getId()));
        List<ParticipantView> participantViews = active.stream()
                .map(p -> ParticipantView.from(p, raisedHands != null && raisedHands.contains(p.getPeerId())))
                .toList();
        List<ChatMessageView> chat = chatMessages
                .findBySessionIdOrderByCreatedAtDesc(session.getId(), PageRequest.of(0, roomProperties.chatSnapshotLimit()))
                .stream().map(ChatMessageView::from).toList().reversed();

        String screenShareFlag = (String) redis.opsForHash().get(flagsKey(session.getId()), "screenShareEnabled");
        boolean screenShareEnabled = screenShareFlag == null || Boolean.parseBoolean(screenShareFlag);

        return new RoomSnapshot(session.getId(), meeting.getId(), meeting.getTitle(), meeting.getLockedAt() != null,
                meeting.isMuteOnEntry(), meeting.isCameraOffOnEntry(), screenShareEnabled,
                roomProperties.screenShareMaxConcurrent(), participantViews, chat, rev);
    }

    private void broadcastPresence(String sessionId, String type, Participation participation, long rev) {
        broadcast(roomTopic(sessionId), new RoomBroadcast(type, rev, ParticipantView.from(participation, false)));
    }

    /** §11.3's "latency optimisation, not a correctness dependency" applies symmetrically here:
     * a broker hiccup must not fail the REST action that already committed to the database. */
    private void broadcast(String destination, RoomBroadcast payload) {
        try {
            messaging.convertAndSend(destination, payload);
        } catch (MessagingException e) {
            log.warn("failed to broadcast to {}: {}", destination, e.getMessage());
        }
    }

    private MeetingSession requireSession(String sessionId) {
        return sessions.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Room not found."));
    }

    private Meeting requireMeeting(MeetingSession session) {
        return meetings.findById(session.getMeetingId())
                .orElseThrow(() -> new AppException(ErrorCode.MEETING_NOT_FOUND, "Meeting not found."));
    }

    private Participation requireActiveParticipation(String sessionId, String peerId) {
        return participations.findBySessionIdAndPeerId(sessionId, peerId)
                .filter(Participation::isActive)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_ADMITTED, "Not an active participant of this room."));
    }

    private void requireActiveParticipant(String sessionId, String peerId) {
        requireActiveParticipation(sessionId, peerId);
    }

    private long bumpRev(String sessionId) {
        Long rev = redis.opsForValue().increment(revKey(sessionId));
        return rev == null ? 1 : rev;
    }

    private long currentRev(String sessionId) {
        String value = redis.opsForValue().get(revKey(sessionId));
        return value == null ? 0 : Long.parseLong(value);
    }

    private String roomTopic(String sessionId) {
        return "/topic/room." + sessionId;
    }

    private String peerTopic(String sessionId, String peerId) {
        return "/topic/room." + sessionId + ".peer." + peerId;
    }

    private String revKey(String sessionId) {
        return "room:rev:" + sessionId;
    }

    private String pendingKey(String sessionId) {
        return "room:pending:" + sessionId;
    }

    private String flagsKey(String sessionId) {
        return "room:flags:" + sessionId;
    }

    private String handsKey(String sessionId) {
        return "room:hands:" + sessionId;
    }
}
