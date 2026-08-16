package com.taut0logy.jmeet.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.jobs")
public record JobsProperties(
        @Min(1) int maxAttempts,
        @NotNull Duration backoffBase,
        @NotNull Duration backoffCap,
        @Min(1) int concurrency) {
}
