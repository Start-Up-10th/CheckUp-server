package com.checkup.checkup.domain.face.dto;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;

import java.util.UUID;

public record FaceSessionResponse(UUID sessionId, AttendancePurpose purpose, String status) {
}
