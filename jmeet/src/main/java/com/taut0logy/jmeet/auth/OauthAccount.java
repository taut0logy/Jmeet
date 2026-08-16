package com.taut0logy.jmeet.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "oauth_account")
public class OauthAccount {

    @Id
    private String id;

    private String userId;
    private String provider;
    private String providerUid;
    private Instant createdAt;

    protected OauthAccount() {
    }

    public OauthAccount(String id, String userId, String provider, String providerUid) {
        this.id = id;
        this.userId = userId;
        this.provider = provider;
        this.providerUid = providerUid;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderUid() {
        return providerUid;
    }
}
