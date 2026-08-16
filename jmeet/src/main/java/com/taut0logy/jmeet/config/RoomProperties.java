package com.taut0logy.jmeet.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.meeting")
public record RoomProperties(@Min(1) int maxParticipants, boolean singleMeetingEnabled,
        @Min(1) int screenShareMaxConcurrent, @Min(1) int chatSnapshotLimit,
        @NotNull Duration reminderLeadTime, @NotNull Duration durationWarning, @NotNull Duration autoEndGrace) {
}
