package com.checkup.checkup.domain.volunteer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 당일 봉사 미이행 처리 요청(#250).
 *
 * @param delta  미이행으로 바꿀 봉사 횟수(-99~99). 0이면 횟수는 그대로 두고 미이행만 기록한다. 차감은 남은 횟수까지만 한다
 * @param reason 사유(선택, 100자 이하). 학생 개인정보는 넣지 않는다
 */
public record VolunteerMissedRequest(
        @Schema(description = "바꿀 봉사 횟수(-99~99). 0이면 횟수는 그대로 두고 미이행만 기록한다. 차감은 남은 횟수까지만 한다", example = "1")
        @NotNull @Min(-99) @Max(99) Integer delta,
        @Schema(description = "사유(선택, 최대 100자). 학생 개인정보는 넣지 않는다", example = "당일 봉사 미이행")
        @Size(max = 100) String reason
) {
}
