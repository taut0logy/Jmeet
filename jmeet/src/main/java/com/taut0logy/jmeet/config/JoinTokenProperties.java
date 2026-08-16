package com.taut0logy.jmeet.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.join-token")
public record JoinTokenProperties(@NotBlank String secret, @NotNull Duration ttl) {
}
