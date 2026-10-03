package com.checkup.checkup.domain.face.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record FaceStatusResponse(
        @Schema(description = "REGISTERED or NOT_REGISTERED.") String status,
        boolean consented,
        boolean eligible,
        boolean enrolled
) {
}
