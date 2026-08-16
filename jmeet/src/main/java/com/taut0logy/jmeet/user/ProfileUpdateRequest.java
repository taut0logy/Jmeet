package com.taut0logy.jmeet.user;

import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @Size(max = 200) String displayName,
        String timezone,
        Boolean defaultMicMuted,
        Boolean defaultCameraOff,
        String preferredAudioInputId,
        String preferredVideoInputId,
        String preferredAudioOutputId) {
}
