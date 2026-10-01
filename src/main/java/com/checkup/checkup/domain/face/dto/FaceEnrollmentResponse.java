package com.checkup.checkup.domain.face.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record FaceEnrollmentResponse(@Schema(description = "REGISTERED.") String status) {
    public static FaceEnrollmentResponse registered() {
        return new FaceEnrollmentResponse("REGISTERED");
    }
}
