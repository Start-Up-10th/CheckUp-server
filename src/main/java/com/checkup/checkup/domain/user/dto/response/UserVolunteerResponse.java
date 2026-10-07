package com.checkup.checkup.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 학생의 남은 봉사 횟수 응답(DEC-020).
 *
 * @param studentId DataGSM 학생 id
 * @param volunteerCount 앞으로 해야 할 봉사 횟수
 */
public record UserVolunteerResponse(
        @Schema(description = "DataGSM 학생 id", example = "1") Long studentId,
        @Schema(description = "앞으로 해야 할 봉사 횟수", example = "2") int volunteerCount
) {
}
