package com.taut0logy.jmeet.auth;

import com.taut0logy.jmeet.common.AppException;
import com.taut0logy.jmeet.common.ErrorCode;
import com.taut0logy.jmeet.common.Ids;
import com.taut0logy.jmeet.config.ClientProperties;
import com.taut0logy.jmeet.mail.EmailMessage;
import com.taut0logy.jmeet.mail.MailService;
import com.taut0logy.jmeet.user.Profile;
import com.taut0logy.jmeet.user.ProfileRepository;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Duration TOKEN_TTL = Duration.ofHours(1);

    private final AppUserRepository users;
    private final ProfileRepository profiles;
    private final AuthTokenRepository tokens;
    private final OauthAccountRepository oauthAccounts;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final ClientProperties clientProperties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AuthService(AppUserRepository users, ProfileRepository profiles, AuthTokenRepository tokens,
            OauthAccountRepository oauthAccounts, PasswordEncoder passwordEncoder, MailService mailService,
            ClientProperties clientProperties, Clock clock) {
        this.users = users;
        this.profiles = profiles;
        this.tokens = tokens;
        this.oauthAccounts = oauthAccounts;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.clientProperties = clientProperties;
        this.clock = clock;
    }

    @Transactional
    public AppUser register(String email, String password, String name) {
        String normalizedEmail = email.toLowerCase();
        if (users.existsByEmail(normalizedEmail)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_REGISTERED, "An account with this email already exists.");
        }
        AppUser user = new AppUser(Ids.next(), normalizedEmail, name, passwordEncoder.encode(password));
        users.save(user);
        profiles.save(new Profile(user.getId(), name));
        sendVerificationEmail(user);
        return user;
    }

    public void resendVerification(String email) {
        users.findByEmail(email.toLowerCase())
                .filter(u -> !u.isEmailVerified())
                .ifPresent(this::sendVerificationEmail);
    }

    private void sendVerificationEmail(AppUser user) {
        String rawToken = issueToken(user.getId(), AuthTokenPurpose.VERIFY_EMAIL);
        String url = clientProperties.baseUrl() + "/verify-email?token=" + rawToken;
        mailService.enqueue(user.getId(), new EmailMessage(
                user.getEmail(), "Verify your email", "notice",
                Map.of("title", "Verify your email", "body", "Confirm your email address to finish setting up your account.",
                        "actionUrl", url, "actionLabel", "Verify email")));
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        AuthToken token = consumeToken(rawToken, AuthTokenPurpose.VERIFY_EMAIL);
        AppUser user = users.findById(token.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "User not found."));
        user.verifyEmail();
    }

    public AppUser authenticate(String email, String rawPassword) {
        AppUser user = users.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS, "Incorrect email or password."));
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS, "Incorrect email or password.");
        }
        if (!user.isEmailVerified()) {
            throw new AppException(ErrorCode.EMAIL_NOT_VERIFIED, "Please verify your email before signing in.");
        }
        return user;
    }

    @Transactional
    public void setInitialPassword(String userId, String newPassword) {
        AppUser user = users.findById(userId).orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "User not found."));
        if (user.getPasswordHash() != null) {
            throw new AppException(ErrorCode.PASSWORD_ALREADY_SET, "This account already has a password.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
    }

    @Transactional
    public void changePassword(String userId, String currentPassword, String newPassword) {
        AppUser user = users.findById(userId).orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "User not found."));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS, "Current password is incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
    }

    public void requestPasswordReset(String email) {
        users.findByEmail(email.toLowerCase()).ifPresent(user -> {
            String rawToken = issueToken(user.getId(), AuthTokenPurpose.RESET_PASSWORD);
            String url = clientProperties.baseUrl() + "/reset-password?token=" + rawToken;
            mailService.enqueue(user.getId(), new EmailMessage(
                    user.getEmail(), "Reset your password", "notice",
                    Map.of("title", "Reset your password", "body", "Use the link below to choose a new password.",
                            "actionUrl", url, "actionLabel", "Reset password")));
        });
    }

    /** Returns the user whose password was reset, so callers can invalidate their other sessions. */
    @Transactional
    public AppUser resetPassword(String rawToken, String newPassword) {
        AuthToken token = consumeToken(rawToken, AuthTokenPurpose.RESET_PASSWORD);
        AppUser user = users.findById(token.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "User not found."));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        return user;
    }

    /** §7.1: match by (provider, providerUid); else link by verified email; else create verified. */
    @Transactional
    public AppUser findOrCreateOAuthUser(String provider, String providerUid, String email, String name) {
        return oauthAccounts.findByProviderAndProviderUid(provider, providerUid)
                .map(account -> users.findById(account.getUserId()).orElseThrow())
                .orElseGet(() -> {
                    AppUser user = users.findByEmail(email.toLowerCase())
                            .filter(AppUser::isEmailVerified)
                            .orElseGet(() -> {
                                AppUser created = new AppUser(Ids.next(), email.toLowerCase(), name, null);
                                created.verifyEmail();
                                users.save(created);
                                profiles.save(new Profile(created.getId(), name));
                                return created;
                            });
                    oauthAccounts.save(new OauthAccount(Ids.next(), user.getId(), provider, providerUid));
                    return user;
                });
    }

    private String issueToken(String userId, AuthTokenPurpose purpose) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        tokens.save(new AuthToken(Ids.next(), userId, purpose, hash(rawToken), Instant.now(clock).plus(TOKEN_TTL)));
        return rawToken;
    }

    private AuthToken consumeToken(String rawToken, AuthTokenPurpose purpose) {
        AuthToken token = tokens.findByTokenHash(hash(rawToken))
                .filter(t -> t.getPurpose() == purpose)
                .orElseThrow(() -> new AppException(ErrorCode.TOKEN_INVALID, "This link is invalid."));
        Instant now = Instant.now(clock);
        if (token.getUsedAt() != null) {
            throw new AppException(ErrorCode.TOKEN_USED, "This link has already been used.");
        }
        if (!token.isUsable(now)) {
            throw new AppException(ErrorCode.TOKEN_EXPIRED, "This link has expired.");
        }
        token.markUsed(now);
        return token;
    }

    private static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes());
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
