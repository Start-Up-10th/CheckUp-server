package com.checkup.checkup.domain.face.dto;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 관리자 얼굴 인식 세션 생성 요청. */
public record FaceSessionCreateRequest(
        @Schema(description = "출석 용도. DORMITORY(기숙사 입소) 또는 STUDY_ROOM(자습실)", example = "DORMITORY")
        @NotNull AttendancePurpose purpose
) {
}
