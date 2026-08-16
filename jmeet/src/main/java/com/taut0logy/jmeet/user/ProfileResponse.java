package com.taut0logy.jmeet.user;

public record ProfileResponse(
        String displayName,
        String avatarUrl,
        String timezone,
        boolean defaultMicMuted,
        boolean defaultCameraOff,
        String preferredAudioInputId,
        String preferredVideoInputId,
        String preferredAudioOutputId) {

    public static ProfileResponse from(Profile profile) {
        return new ProfileResponse(
                profile.getDisplayName(),
                profile.getAvatarUrl(),
                profile.getTimezone(),
                profile.isDefaultMicMuted(),
                profile.isDefaultCameraOff(),
                profile.getPreferredAudioInputId(),
                profile.getPreferredVideoInputId(),
                profile.getPreferredAudioOutputId());
    }
}
