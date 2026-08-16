package com.taut0logy.jmeet.auth;

import com.taut0logy.jmeet.common.AppException;
import com.taut0logy.jmeet.common.ErrorCode;
import com.taut0logy.jmeet.common.RateLimiter;
import com.taut0logy.jmeet.config.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

@RestController
public class AuthController {

    private final AuthService authService;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final RateLimiter rateLimiter;
    private final AuthProperties authProperties;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthService authService, FindByIndexNameSessionRepository<? extends Session> sessions,
            RateLimiter rateLimiter, AuthProperties authProperties) {
        this.authService = authService;
        this.sessions = sessions;
        this.rateLimiter = rateLimiter;
        this.authProperties = authProperties;
    }

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        limit("register", httpRequest, request.email(), authProperties.rateLimit().register());
        authService.register(request.email(), request.password(), request.name());
    }

    @PostMapping("/api/auth/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody TokenRequest request) {
        authService.verifyEmail(request.token());
    }

    @PostMapping("/api/auth/verify-email/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerification(@Valid @RequestBody EmailRequest request, HttpServletRequest httpRequest) {
        limit("verify-resend", httpRequest, request.email(), authProperties.rateLimit().register());
        authService.resendVerification(request.email());
    }

    @PostMapping("/api/auth/login")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        limit("login", httpRequest, request.email(), authProperties.rateLimit().login());
        AppUser user = authService.authenticate(request.email(), request.password());
        establishSession(user, httpRequest, httpResponse);
    }

    @PostMapping("/api/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
    }

    @PostMapping("/api/auth/request-password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestPasswordReset(@Valid @RequestBody EmailRequest request, HttpServletRequest httpRequest) {
        limit("password-reset", httpRequest, request.email(), authProperties.rateLimit().reset());
        authService.requestPasswordReset(request.email());
    }

    @PostMapping("/api/auth/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        AppUser user = authService.resetPassword(request.token(), request.newPassword());
        sessions.findByPrincipalName(user.getEmail()).keySet().forEach(sessions::deleteById);
    }

    @GetMapping("/api/auth/sessions")
    public List<SessionSummary> listSessions(Authentication authentication, HttpServletRequest request) {
        String currentId = request.getSession(false) != null ? request.getSession(false).getId() : null;
        return sessions.findByPrincipalName(authentication.getName()).values().stream()
                .map(s -> new SessionSummary(
                        s.getId(),
                        s.getId().equals(currentId),
                        Instant.ofEpochMilli(s.getCreationTime().toEpochMilli()).atZone(ZoneOffset.UTC).toString(),
                        Instant.ofEpochMilli(s.getLastAccessedTime().toEpochMilli()).atZone(ZoneOffset.UTC).toString()))
                .sorted(Comparator.comparing(SessionSummary::current).reversed())
                .toList();
    }

    @DeleteMapping("/api/auth/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(@PathVariable String sessionId, Authentication authentication) {
        if (!sessions.findByPrincipalName(authentication.getName()).containsKey(sessionId)) {
            throw new AppException(ErrorCode.SESSION_NOT_FOUND, "Session not found.");
        }
        sessions.deleteById(sessionId);
    }

    @DeleteMapping("/api/auth/sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeOtherSessions(Authentication authentication, HttpServletRequest request) {
        String currentId = request.getSession(false) != null ? request.getSession(false).getId() : null;
        sessions.findByPrincipalName(authentication.getName()).keySet().stream()
                .filter(id -> !id.equals(currentId))
                .forEach(sessions::deleteById);
    }

    private void limit(String bucket, HttpServletRequest request, String email, AuthProperties.Limit config) {
        String ip = request.getRemoteAddr();
        rateLimiter.check(bucket + ":ip:" + ip, config.limit(), config.period());
        rateLimiter.check(bucket + ":email:" + email.toLowerCase(), config.limit(), config.period());
    }

    private void establishSession(AppUser user, HttpServletRequest request, HttpServletResponse response) {
        AuthPrincipal principal = AuthPrincipal.from(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        ((org.springframework.security.authentication.AbstractAuthenticationToken) authentication)
                .setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
