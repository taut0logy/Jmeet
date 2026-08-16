package com.taut0logy.jmeet.meeting;

import com.taut0logy.jmeet.auth.AuthPrincipal;
import com.taut0logy.jmeet.common.RateLimiter;
import com.taut0logy.jmeet.config.AuthProperties;
import com.taut0logy.jmeet.meeting.member.InviteMemberRequest;
import com.taut0logy.jmeet.meeting.member.MemberResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeetingController {

    private static final Duration DEFAULT_LOOKAHEAD = Duration.ofDays(90);

    private final MeetingService meetingService;
    private final RateLimiter rateLimiter;
    private final AuthProperties authProperties;
    private final Clock clock;

    public MeetingController(MeetingService meetingService, RateLimiter rateLimiter, AuthProperties authProperties,
            Clock clock) {
        this.meetingService = meetingService;
        this.rateLimiter = rateLimiter;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    @GetMapping("/api/meetings")
    public List<MeetingSummary> list(@AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String role) {
        Instant effectiveFrom = from != null ? from : Instant.now(clock);
        Instant effectiveTo = to != null ? to : effectiveFrom.plus(DEFAULT_LOOKAHEAD);
        return meetingService.list(principal.userId(), effectiveFrom, effectiveTo, role);
    }

    @PostMapping("/api/meetings")
    @ResponseStatus(HttpStatus.CREATED)
    public MeetingResponse create(@AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody MeetingCreateRequest request) {
        return meetingService.create(principal.userId(), request);
    }

    @GetMapping("/api/meetings/{id}")
    public MeetingResponse detail(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        Instant effectiveFrom = from != null ? from : Instant.now(clock);
        Instant effectiveTo = to != null ? to : effectiveFrom.plus(DEFAULT_LOOKAHEAD);
        return meetingService.detail(principal.userId(), id, effectiveFrom, effectiveTo);
    }

    @PatchMapping("/api/meetings/{id}")
    public MeetingResponse update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id,
            @RequestBody MeetingUpdateRequest request, @RequestParam(required = false) EditScope scope,
            @RequestParam(required = false) Instant occurrenceStartsAt) {
        return meetingService.update(principal.userId(), id, request, scope, occurrenceStartsAt);
    }

    @DeleteMapping("/api/meetings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id,
            @RequestParam(required = false) EditScope scope, @RequestParam(required = false) Instant occurrenceStartsAt) {
        meetingService.cancel(principal.userId(), id, scope, occurrenceStartsAt);
    }

    @PostMapping("/api/meetings/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse addMember(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id,
            @RequestBody InviteMemberRequest request) {
        return meetingService.addMember(principal.userId(), id, request);
    }

    @DeleteMapping("/api/meetings/{id}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id,
            @PathVariable String memberId) {
        meetingService.removeMember(principal.userId(), id, memberId);
    }

    @GetMapping("/api/meetings/by-code/{code}")
    public MeetingLobbyResponse lobby(@PathVariable String code) {
        return meetingService.lobby(code);
    }

    @PostMapping("/api/meetings/by-code/{code}/join-token")
    public JoinTokenResponse joinToken(@PathVariable String code, @RequestBody(required = false) JoinTokenRequest request,
            @AuthenticationPrincipal AuthPrincipal principal, HttpServletRequest httpRequest) {
        AuthProperties.Limit limit = authProperties.rateLimit().joinToken();
        String ip = httpRequest.getRemoteAddr();
        rateLimiter.check("join-token:ip:" + ip, limit.limit(), limit.period());
        if (principal != null) {
            rateLimiter.check("join-token:user:" + principal.userId(), limit.limit(), limit.period());
        }

        String displayName = request != null ? request.displayName() : null;
        boolean hasSession = principal != null;
        return meetingService.mintJoinToken(code, displayName, hasSession ? principal.userId() : null, hasSession);
    }
}
