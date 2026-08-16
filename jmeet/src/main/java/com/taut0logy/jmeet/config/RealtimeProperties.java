package com.taut0logy.jmeet.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.realtime")
public record RealtimeProperties(@Min(1) int stompRelayPort) {
}
