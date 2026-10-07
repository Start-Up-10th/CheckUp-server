package com.checkup.checkup.domain.face.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** 얼굴 AI 서버 연결, 업로드 크기 제한, 짧게 유지되는 인식 세션 설정값. */
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
        @Positive int maxConcurrentEnrollments,
        @NotNull Duration enrollmentWaitTimeout,
        @NotNull Duration sessionIdleTimeout,
        @Positive long cleanupIntervalMs,
        @NotBlank String consentVersion,
        @Positive int maxCandidates
) {
    /** 얼굴 인식 세션 후보 상한의 기본값. 기숙사생 약 200명(scope.md)과 같다. 계약은 "설정된 최대치"를 넘으면 422라고 한다. */
    public static final int DEFAULT_MAX_CANDIDATES = 200;

    /** 후보 상한을 따로 정하지 않는 호출용. {@link #DEFAULT_MAX_CANDIDATES}를 쓴다. */
    public FaceProperties(
            String aiBaseUrl,
            String serviceToken,
            Duration connectTimeout,
            Duration responseTimeout,
            long maxUploadBytes,
            long maxFrameBytes,
            Duration minFrameInterval,
            int maxConcurrentFrames,
            int maxConcurrentEnrollments,
            Duration enrollmentWaitTimeout,
            Duration sessionIdleTimeout,
            long cleanupIntervalMs,
            String consentVersion
    ) {
        this(aiBaseUrl, serviceToken, connectTimeout, responseTimeout, maxUploadBytes, maxFrameBytes,
                minFrameInterval, maxConcurrentFrames, maxConcurrentEnrollments, enrollmentWaitTimeout,
                sessionIdleTimeout, cleanupIntervalMs, consentVersion, DEFAULT_MAX_CANDIDATES);
    }

    @ConstructorBinding
    public FaceProperties {
        if (aiBaseUrl != null) {
            aiBaseUrl = aiBaseUrl.replaceFirst("/+$", "");
        }
        requirePositive(connectTimeout, "connect-timeout");
        requirePositive(responseTimeout, "response-timeout");
        requirePositive(minFrameInterval, "min-frame-interval");
        requirePositive(sessionIdleTimeout, "session-idle-timeout");
        if (enrollmentWaitTimeout != null && enrollmentWaitTimeout.isNegative()) {
            throw new IllegalArgumentException("enrollment-wait-timeout must not be negative");
        }
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

    /** 첫 프레임이 404를 반환한 뒤 준비 상태 확인, 세션 재생성, 프레임 재시도를 마칠 시간을 포함한다. */
    public Duration frameRecoveryLease() {
        return connectTimeout.plus(responseTimeout()).multipliedBy(3).plusSeconds(30);
    }
}
