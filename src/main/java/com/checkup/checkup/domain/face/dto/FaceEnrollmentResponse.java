package com.checkup.checkup.domain.face.dto;

public record FaceEnrollmentResponse(String status) {
    public static FaceEnrollmentResponse registered() {
        return new FaceEnrollmentResponse("registered");
    }
}
