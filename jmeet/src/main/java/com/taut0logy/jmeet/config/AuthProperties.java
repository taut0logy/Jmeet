package com.taut0logy.jmeet.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.auth")
public record AuthProperties(@NotNull Duration sessionTtl, @Valid @NotNull RateLimits rateLimit) {

    public record RateLimits(
            @Valid @NotNull Limit login,
            @Valid @NotNull Limit register,
            @Valid @NotNull Limit reset,
            @Valid @NotNull Limit joinToken) {
    }

    public record Limit(@Min(1) int limit, @NotNull Duration period) {
    }
}
