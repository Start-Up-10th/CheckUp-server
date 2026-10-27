package com.checkup.checkup.domain.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 지금 용도별로 출석을 받는 중인지. 어느 관리자·기기의 세션인지, 세션 id·토큰은 주지 않는다(#260).
 *
 * @param dormitory 기숙사 입소 출석을 받는 중인지
 * @param studyRoom 자습실 출석을 받는 중인지
 */
public record AttendanceActiveResponse(
        @Schema(description = "기숙사 입소(DORMITORY) 출석을 받는 중인지") boolean dormitory,
        @Schema(description = "자습실(STUDY_ROOM) 출석을 받는 중인지") boolean studyRoom
) {
}
