package com.taut0logy.jmeet.meeting;

import com.taut0logy.jmeet.auth.AppUser;
import com.taut0logy.jmeet.auth.AppUserRepository;
import com.taut0logy.jmeet.auth.GuestJoinTokenService;
import com.taut0logy.jmeet.common.AppException;
import com.taut0logy.jmeet.common.ErrorCode;
import com.taut0logy.jmeet.common.Ids;
import com.taut0logy.jmeet.meeting.member.InviteMemberRequest;
import com.taut0logy.jmeet.meeting.member.MemberResponse;
import com.taut0logy.jmeet.meeting.member.MemberRole;
import com.taut0logy.jmeet.meeting.member.MeetingMember;
import com.taut0logy.jmeet.meeting.member.MeetingMemberRepository;
import com.taut0logy.jmeet.meeting.recurrence.MeetingOccurrence;
import com.taut0logy.jmeet.meeting.recurrence.MeetingOccurrenceRepository;
import com.taut0logy.jmeet.meeting.recurrence.MeetingSeriesOverride;
import com.taut0logy.jmeet.meeting.recurrence.MeetingSeriesOverrideRepository;
import com.taut0logy.jmeet.meeting.recurrence.OccurrenceStatus;
import com.taut0logy.jmeet.meeting.recurrence.OccurrenceView;
import com.taut0logy.jmeet.meeting.recurrence.RecurrenceExpander;
import com.taut0logy.jmeet.meeting.recurrence.SeriesDef;
import com.taut0logy.jmeet.user.Profile;
import com.taut0logy.jmeet.user.ProfileRepository;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetingService {

    private static final int MAX_CODE_ATTEMPTS = 5;

    private final MeetingRepository meetings;
    private final MeetingMemberRepository members;
    private final MeetingOccurrenceRepository occurrences;
    private final MeetingSeriesOverrideRepository overrides;
    private final AppUserRepository users;
    private final ProfileRepository profiles;
    private final GuestJoinTokenService joinTokens;

    public MeetingService(MeetingRepository meetings, MeetingMemberRepository members,
            MeetingOccurrenceRepository occurrences, MeetingSeriesOverrideRepository overrides,
            AppUserRepository users, ProfileRepository profiles, GuestJoinTokenService joinTokens) {
        this.meetings = meetings;
        this.members = members;
        this.occurrences = occurrences;
        this.overrides = overrides;
        this.users = users;
        this.profiles = profiles;
        this.joinTokens = joinTokens;
    }

    @Transactional
    public MeetingResponse create(String ownerId, MeetingCreateRequest request) {
        if (request.kind() == MeetingKind.SCHEDULED && request.startsAt() == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "startsAt is required for a scheduled meeting.");
        }
        Instant startsAt = request.kind() == MeetingKind.INSTANT ? Instant.now() : request.startsAt();

        Meeting meeting = new Meeting(Ids.next(), generateCode(), request.title(), ownerId, request.kind(), startsAt);
        if (request.description() != null) meeting.setDescription(request.description());
        if (request.durationMin() != null) meeting.setDurationMin(request.durationMin());
        if (request.timezone() != null) meeting.setTimezone(request.timezone());
        if (request.kind() == MeetingKind.SCHEDULED && request.rrule() != null) meeting.setRrule(request.rrule());
        if (request.seriesEndsAt() != null) meeting.setSeriesEndsAt(request.seriesEndsAt());
        if (request.access() != null) meeting.setAccess(request.access());
        if (request.waitingRoom() != null) meeting.setWaitingRoom(request.waitingRoom());
        if (request.allowGuests() != null) meeting.setAllowGuests(request.allowGuests());
        if (request.muteOnEntry() != null) meeting.setMuteOnEntry(request.muteOnEntry());
        if (request.cameraOffOnEntry() != null) meeting.setCameraOffOnEntry(request.cameraOffOnEntry());

        meetings.save(meeting);
        return MeetingResponse.from(meeting, List.of(), List.of());
    }

    public MeetingResponse detail(String userId, String meetingId, Instant from, Instant to) {
        Meeting meeting = getViewable(userId, meetingId);
        return toResponse(meeting, from, to);
    }

    public List<MeetingSummary> list(String userId, Instant from, Instant to, String roleFilter) {
        Set<Meeting> candidates = new LinkedHashSet<>();
        if (!"member".equals(roleFilter)) {
            candidates.addAll(meetings.findByOwnerId(userId));
        }
        if (!"owner".equals(roleFilter)) {
            members.findByUserId(userId).forEach(m -> meetings.findById(m.getMeetingId()).ifPresent(candidates::add));
        }

        return candidates.stream()
                .filter(meeting -> overlapsRange(meeting, from, to))
                .map(MeetingSummary::from)
                .sorted((a, b) -> {
                    if (a.startsAt() == null) return 1;
                    if (b.startsAt() == null) return -1;
                    return a.startsAt().compareTo(b.startsAt());
                })
                .toList();
    }

    @Transactional
    public MeetingResponse update(String userId, String meetingId, MeetingUpdateRequest request, EditScope scope,
            Instant occurrenceStartsAt) {
        Meeting meeting = getEditable(userId, meetingId);
        EditScope resolvedScope = meeting.isRecurring() ? scope : EditScope.ALL;
        if (resolvedScope == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "scope is required for a recurring meeting.");
        }
        if (request.rrule() != null && resolvedScope != EditScope.ALL) {
            throw new AppException(ErrorCode.PATTERN_CHANGE_REQUIRES_SCOPE_ALL,
                    "Changing the recurrence pattern requires scope=all.");
        }
        if (resolvedScope != EditScope.ALL && occurrenceStartsAt == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "occurrenceStartsAt is required for this scope.");
        }

        switch (resolvedScope) {
            case ALL -> applyAll(meeting, request);
            case THIS -> applyThis(meeting, request, occurrenceStartsAt);
            case THIS_AND_FOLLOWING -> applyThisAndFollowing(meeting, request, occurrenceStartsAt);
        }

        Instant from = occurrenceStartsAt != null ? occurrenceStartsAt : Instant.now();
        return toResponse(meeting, from, from.plusSeconds(60L * 60 * 24 * 90));
    }

    @Transactional
    public void cancel(String userId, String meetingId, EditScope scope, Instant occurrenceStartsAt) {
        Meeting meeting = getEditable(userId, meetingId);
        if (!meeting.isRecurring()) {
            meeting.setStatus(MeetingStatus.CANCELLED);
            return;
        }
        if (scope == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "scope is required for a recurring meeting.");
        }
        switch (scope) {
            case ALL -> meeting.setStatus(MeetingStatus.CANCELLED);
            case THIS -> {
                if (occurrenceStartsAt == null) {
                    throw new AppException(ErrorCode.VALIDATION_ERROR, "occurrenceStartsAt is required for this scope.");
                }
                occurrenceFor(meeting.getId(), occurrenceStartsAt).cancel();
            }
            case THIS_AND_FOLLOWING -> {
                if (occurrenceStartsAt == null) {
                    throw new AppException(ErrorCode.VALIDATION_ERROR, "occurrenceStartsAt is required for this scope.");
                }
                meeting.setSeriesEndsAt(occurrenceStartsAt.minusSeconds(1));
            }
        }
    }

    @Transactional
    public MemberResponse addMember(String userId, String meetingId, InviteMemberRequest request) {
        Meeting meeting = getEditable(userId, meetingId);
        if ((request.email() == null || request.email().isBlank()) && request.userId() == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Either email or userId is required.");
        }
        MemberRole role = request.role() != null ? request.role() : MemberRole.INVITEE;

        String invitedUserId = request.userId() != null ? request.userId()
                : users.findByEmail(request.email()).map(AppUser::getId).orElse(null);

        if (invitedUserId != null) {
            String resolvedUserId = invitedUserId;
            MeetingMember member = members.findByMeetingIdAndUserId(meeting.getId(), invitedUserId)
                    .orElseGet(() -> members.save(new MeetingMember(Ids.next(), meeting.getId(), resolvedUserId, null, role)));
            return MemberResponse.from(member);
        }

        MeetingMember member = members.findByMeetingIdAndEmailAndUserIdIsNull(meeting.getId(), request.email())
                .orElseGet(() -> members.save(new MeetingMember(Ids.next(), meeting.getId(), null, request.email(), role)));
        return MemberResponse.from(member);
    }

    @Transactional
    public void removeMember(String userId, String meetingId, String memberId) {
        getEditable(userId, meetingId);
        MeetingMember member = members.findById(memberId)
                .filter(m -> m.getMeetingId().equals(meetingId))
                .orElseThrow(() -> new AppException(ErrorCode.MEMBER_NOT_FOUND, "Member not found."));
        members.delete(member);
    }

    public MeetingLobbyResponse lobby(String code) {
        Meeting meeting = meetings.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.MEETING_NOT_FOUND, "Meeting not found."));
        String hostName = users.findById(meeting.getOwnerId()).map(AppUser::getName).orElse(null);
        return new MeetingLobbyResponse(meeting.getTitle(), hostName, meeting.getStatus(), meeting.getAccess(),
                meeting.getWaitingRoom(), meeting.isAllowGuests());
    }

    @Transactional
    public JoinTokenResponse mintJoinToken(String code, String requestedDisplayName, String userId, boolean hasSession) {
        Meeting meeting = meetings.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.MEETING_NOT_FOUND, "Meeting not found."));

        boolean isOwner = hasSession && meeting.getOwnerId().equals(userId);
        MemberRole memberRole = hasSession
                ? members.findByMeetingIdAndUserId(meeting.getId(), userId).map(MeetingMember::getRole).orElse(null)
                : null;

        JoinContext ctx = new JoinContext(meeting.getStatus(), meeting.getLockedAt() != null, meeting.getAccess(),
                meeting.isAllowGuests(), meeting.getWaitingRoom(), hasSession, isOwner, memberRole, requestedDisplayName);
        JoinDecision decision = JoinAccessEvaluator.evaluate(ctx);

        String displayName = hasSession
                ? profiles.findById(userId).map(Profile::getDisplayName).orElse(requestedDisplayName)
                : requestedDisplayName;

        String token = joinTokens.mint(meeting.getCode(), displayName, hasSession ? userId : null,
                decision.role().name(), decision.requiresApproval());
        return new JoinTokenResponse(token);
    }

    public Meeting requireHostOrCohost(String userId, String meetingId) {
        return getEditable(userId, meetingId);
    }

    public Meeting requireMember(String userId, String meetingId) {
        return getViewable(userId, meetingId);
    }

    private Meeting getViewable(String userId, String meetingId) {
        Meeting meeting = meetings.findById(meetingId)
                .orElseThrow(() -> new AppException(ErrorCode.MEETING_NOT_FOUND, "Meeting not found."));
        boolean isOwner = meeting.getOwnerId().equals(userId);
        boolean isMember = members.findByMeetingIdAndUserId(meetingId, userId).isPresent();
        if (!isOwner && !isMember) {
            throw new AppException(ErrorCode.FORBIDDEN, "You do not have access to this meeting.");
        }
        return meeting;
    }

    private Meeting getEditable(String userId, String meetingId) {
        Meeting meeting = meetings.findById(meetingId)
                .orElseThrow(() -> new AppException(ErrorCode.MEETING_NOT_FOUND, "Meeting not found."));
        boolean isOwner = meeting.getOwnerId().equals(userId);
        MemberRole role = members.findByMeetingIdAndUserId(meetingId, userId).map(MeetingMember::getRole).orElse(null);
        if (!isOwner && role != MemberRole.HOST && role != MemberRole.COHOST) {
            throw new AppException(ErrorCode.FORBIDDEN, "You do not have permission to edit this meeting.");
        }
        return meeting;
    }

    private MeetingOccurrence occurrenceFor(String meetingId, Instant originalStartsAt) {
        return occurrences.findByMeetingIdAndOriginalStartsAt(meetingId, originalStartsAt)
                .orElseGet(() -> new MeetingOccurrence(Ids.next(), meetingId, originalStartsAt, OccurrenceStatus.SCHEDULED));
    }

    private void applyAll(Meeting meeting, MeetingUpdateRequest request) {
        if (request.title() != null) meeting.setTitle(request.title());
        if (request.description() != null) meeting.setDescription(request.description());
        if (request.startsAt() != null) meeting.setStartsAt(request.startsAt());
        if (request.durationMin() != null) meeting.setDurationMin(request.durationMin());
        if (request.timezone() != null) meeting.setTimezone(request.timezone());
        if (request.rrule() != null) meeting.setRrule(request.rrule());
        if (request.seriesEndsAt() != null) meeting.setSeriesEndsAt(request.seriesEndsAt());
        if (request.access() != null) meeting.setAccess(request.access());
        if (request.waitingRoom() != null) meeting.setWaitingRoom(request.waitingRoom());
        if (request.allowGuests() != null) meeting.setAllowGuests(request.allowGuests());
        if (request.muteOnEntry() != null) meeting.setMuteOnEntry(request.muteOnEntry());
        if (request.cameraOffOnEntry() != null) meeting.setCameraOffOnEntry(request.cameraOffOnEntry());
    }

    private void applyThis(Meeting meeting, MeetingUpdateRequest request, Instant originalStartsAt) {
        MeetingOccurrence occurrence = occurrenceFor(meeting.getId(), originalStartsAt);
        Instant newStartsAt = request.startsAt() != null ? request.startsAt() : originalStartsAt;
        Integer durationMin = request.durationMin() != null ? request.durationMin() : meeting.getDurationMin();
        String title = request.title() != null ? request.title() : meeting.getTitle();
        occurrence.move(newStartsAt, durationMin, title);
        occurrences.save(occurrence);
    }

    private void applyThisAndFollowing(Meeting meeting, MeetingUpdateRequest request, Instant fromStartsAt) {
        MeetingSeriesOverride override = new MeetingSeriesOverride(Ids.next(), meeting.getId(), fromStartsAt,
                request.title(), request.durationMin(), request.startTimeLocal());
        overrides.save(override);
    }

    private boolean overlapsRange(Meeting meeting, Instant from, Instant to) {
        SeriesDef series = toSeriesDef(meeting);
        List<OccurrenceView> expanded = RecurrenceExpander.expand(series, occurrences.findByMeetingId(meeting.getId()),
                overrides.findByMeetingIdOrderByFromStartsAt(meeting.getId()), from, to);
        return !expanded.isEmpty();
    }

    private MeetingResponse toResponse(Meeting meeting, Instant from, Instant to) {
        List<MemberResponse> memberResponses = members.findByMeetingId(meeting.getId()).stream()
                .map(MemberResponse::from)
                .toList();
        SeriesDef series = toSeriesDef(meeting);
        List<OccurrenceView> expanded = RecurrenceExpander.expand(series, occurrences.findByMeetingId(meeting.getId()),
                overrides.findByMeetingIdOrderByFromStartsAt(meeting.getId()), from, to);
        return MeetingResponse.from(meeting, memberResponses, expanded);
    }

    private SeriesDef toSeriesDef(Meeting meeting) {
        int durationMin = meeting.getDurationMin() != null ? meeting.getDurationMin() : 60;
        return new SeriesDef(meeting.getRrule(), meeting.getStartsAt(), durationMin, meeting.getTitle(),
                meeting.getTimezone(), meeting.getSeriesEndsAt());
    }

    private String generateCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = MeetingCodeGenerator.generate();
            if (!meetings.existsByCode(code)) return code;
        }
        throw new IllegalStateException("failed to generate a unique meeting code after " + MAX_CODE_ATTEMPTS + " attempts");
    }
}
