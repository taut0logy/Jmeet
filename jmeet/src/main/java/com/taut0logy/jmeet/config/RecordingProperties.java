package com.taut0logy.jmeet.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.recording")
public record RecordingProperties(@NotBlank String bucket, @NotBlank String region, @NotBlank String endpoint,
        boolean pathStyle, String accessKey, String secretKey, @NotBlank String layoutUrl,
        @NotNull Duration maxDuration, String egressEndpoint) {

    public String egressEndpointOrDefault() {
        return (egressEndpoint == null || egressEndpoint.isBlank()) ? endpoint : egressEndpoint;
    }
}
