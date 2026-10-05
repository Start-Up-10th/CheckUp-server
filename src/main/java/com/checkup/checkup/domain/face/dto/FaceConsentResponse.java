package com.checkup.checkup.domain.face.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record FaceConsentResponse(
        @Schema(description = "얼굴 정보 처리에 동의했는지") boolean consented,
        @Schema(description = "동의한 얼굴 정보 처리 문구 버전") String version,
        @Schema(description = "얼굴 정보 처리에 처음 동의한 시각", example = "2026-10-06T00:00:00Z") Instant consentedAt
) {
}
