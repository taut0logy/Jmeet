package com.taut0logy.jmeet.user;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "profile")
public class Profile {

    @Id
    private String userId;

    private String displayName;
    private String avatarUrl;
    private String timezone;
    private boolean defaultMicMuted;
    private boolean defaultCameraOff;
    private String preferredAudioInputId;
    private String preferredVideoInputId;
    private String preferredAudioOutputId;
    private Instant createdAt;
    private Instant updatedAt;

    protected Profile() {
    }

    public Profile(String userId, String displayName) {
        this.userId = userId;
        this.displayName = displayName;
        this.timezone = "UTC";
        this.defaultMicMuted = false;
        this.defaultCameraOff = false;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getTimezone() {
        return timezone;
    }

    public boolean isDefaultMicMuted() {
        return defaultMicMuted;
    }

    public boolean isDefaultCameraOff() {
        return defaultCameraOff;
    }

    public String getPreferredAudioInputId() {
        return preferredAudioInputId;
    }

    public String getPreferredVideoInputId() {
        return preferredVideoInputId;
    }

    public String getPreferredAudioOutputId() {
        return preferredAudioOutputId;
    }

    /** Applies each field only if present — every caller sends a different subset. */
    public void applyPartial(ProfileUpdateRequest request) {
        if (request.displayName() != null) this.displayName = request.displayName();
        if (request.timezone() != null) this.timezone = request.timezone();
        if (request.defaultMicMuted() != null) this.defaultMicMuted = request.defaultMicMuted();
        if (request.defaultCameraOff() != null) this.defaultCameraOff = request.defaultCameraOff();
        if (request.preferredAudioInputId() != null) this.preferredAudioInputId = request.preferredAudioInputId();
        if (request.preferredVideoInputId() != null) this.preferredVideoInputId = request.preferredVideoInputId();
        if (request.preferredAudioOutputId() != null) this.preferredAudioOutputId = request.preferredAudioOutputId();
        this.updatedAt = Instant.now();
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
        this.updatedAt = Instant.now();
    }
}
