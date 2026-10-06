package com.checkup.checkup.domain.room.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관리자 호실 전개도의 학생 한 명 수동 출석 수정 결과.
 *
 * @param studentId DataGSM 학생 id
 * @param attended  수정 뒤 오늘 운영일 기숙사 출석 상태
 */
public record ManualRoomAttendanceResponse(
        @Schema(description = "DataGSM 학생 id", example = "1") @JsonProperty("student_id") Long studentId,
        @Schema(description = "수정 뒤 오늘 운영일 기숙사 출석 상태") @JsonProperty("attended") boolean attended
) {
}
