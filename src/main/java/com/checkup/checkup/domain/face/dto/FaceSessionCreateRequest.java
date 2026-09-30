package com.checkup.checkup.domain.face.dto;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import jakarta.validation.constraints.NotNull;

public record FaceSessionCreateRequest(@NotNull AttendancePurpose purpose) {
}
