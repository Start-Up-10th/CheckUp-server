package com.checkup.checkup.domain.volunteer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 봉사 횟수 여러 회 조정 요청(#139).
 *
 * @param delta  바꿀 횟수. 양수는 추가, 음수는 차감이며 -99~99, 0 제외. 차감은 남은 횟수까지만 한다
 * @param reason 사유(선택, 100자 이하). 학생 개인정보는 넣지 않는다
 */
public record VolunteerAdjustRequest(
        @Schema(description = "바꿀 횟수. 양수는 추가, 음수는 차감(-99~99, 0 제외). 차감은 남은 횟수까지만 한다", example = "-3")
        @NotNull @Min(-99) @Max(99) Integer delta,
        @Schema(description = "사유(선택, 최대 100자). 학생 개인정보는 넣지 않는다", example = "청소 당번 대체")
        @Size(max = 100) String reason
) {
}
