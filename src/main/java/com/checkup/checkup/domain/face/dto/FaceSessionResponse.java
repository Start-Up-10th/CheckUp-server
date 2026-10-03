package com.checkup.checkup.domain.face.dto;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record FaceSessionResponse(
        UUID sessionId,
        AttendancePurpose purpose,
        @Schema(description = "ACTIVE after AI readiness and session setup succeed.") String status
) {
}
