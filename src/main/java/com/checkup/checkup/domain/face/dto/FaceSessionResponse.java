package com.checkup.checkup.domain.face.dto;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record FaceSessionResponse(
        @Schema(description = "얼굴 인식 세션 id. 프레임 전송·종료 경로에 쓴다") UUID sessionId,
        @Schema(description = "출석 용도. DORMITORY 또는 STUDY_ROOM") AttendancePurpose purpose,
        @Schema(description = "AI 준비 확인과 세션 생성이 성공하면 ACTIVE") String status
) {
}
