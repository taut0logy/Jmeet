package com.taut0logy.jmeet.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.storage")
public record StorageProperties(
        @NotNull Driver driver,
        String bucket,
        String region,
        String endpoint,
        boolean pathStyle,
        @NotBlank String localPath) {

    public enum Driver { LOCAL, S3 }
}
