package com.taut0logy.jmeet.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "auth_token")
public class AuthToken {

    @Id
    private String id;

    private String userId;

    @Enumerated(EnumType.STRING)
    private AuthTokenPurpose purpose;

    private String tokenHash;
    private Instant expiresAt;
    private Instant usedAt;
    private Instant createdAt;

    protected AuthToken() {
    }

    public AuthToken(String id, String userId, AuthTokenPurpose purpose, String tokenHash, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public AuthTokenPurpose getPurpose() {
        return purpose;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }
}
