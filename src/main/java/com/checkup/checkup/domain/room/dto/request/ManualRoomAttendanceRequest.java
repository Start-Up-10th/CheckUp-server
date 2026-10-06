package com.checkup.checkup.domain.room.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 관리자 호실 전개도의 학생 한 명 수동 출석 수정 요청.
 *
 * @param attended 지정할 오늘 운영일 기숙사 출석 상태. 출석이면 true
 */
public record ManualRoomAttendanceRequest(
        @Schema(description = "지정할 오늘 운영일 기숙사 출석 상태. 출석이면 true", example = "true") @NotNull Boolean attended
) {
}
