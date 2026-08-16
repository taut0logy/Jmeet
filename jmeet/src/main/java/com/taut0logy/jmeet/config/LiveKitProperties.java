package com.taut0logy.jmeet.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.livekit")
public record LiveKitProperties(@NotBlank String host, @NotBlank String apiKey, @NotBlank String apiSecret) {
}
