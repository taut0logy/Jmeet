package com.taut0logy.jmeet.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.client")
public record ClientProperties(@NotBlank String baseUrl) {
}
