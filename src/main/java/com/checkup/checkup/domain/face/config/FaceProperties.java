package com.checkup.checkup.domain.face.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Face API, upload limits, and short-lived recognition-session settings. */
@Validated
@ConfigurationProperties("checkup.face")
public record FaceProperties(
        @NotBlank String aiBaseUrl,
        @NotBlank String serviceToken,
        @NotNull Duration connectTimeout,
        @NotNull Duration responseTimeout,
        @Positive long maxUploadBytes,
        @Positive long maxFrameBytes,
        @NotNull Duration minFrameInterval,
        @Positive int maxConcurrentFrames,
        @NotNull Duration sessionIdleTimeout,
        @Positive long cleanupIntervalMs,
        @NotBlank String consentVersion
) {
    public FaceProperties {
        requirePositive(connectTimeout, "connect-timeout");
        requirePositive(responseTimeout, "response-timeout");
        requirePositive(minFrameInterval, "min-frame-interval");
        requirePositive(sessionIdleTimeout, "session-idle-timeout");
        if (maxUploadBytes > Integer.MAX_VALUE - 1L || maxFrameBytes > Integer.MAX_VALUE - 1L) {
            throw new IllegalArgumentException("face request body limits must fit in a byte array");
        }
        if (cleanupIntervalMs <= 0) {
            throw new IllegalArgumentException("cleanup-interval-ms must be positive");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value != null && (value.isZero() || value.isNegative())) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
