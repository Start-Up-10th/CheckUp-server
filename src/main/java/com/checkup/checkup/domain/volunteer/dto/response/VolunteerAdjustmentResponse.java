package com.checkup.checkup.domain.volunteer.dto.response;

import com.checkup.checkup.domain.volunteer.entity.VolunteerAdjustment;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 봉사 횟수 조정 이력 한 줄(#140). 관리자 화면에서 학생별로 최신순으로 보여준다.
 *
 * @param createdAt      조정한 시각
 * @param delta          실제로 바뀐 횟수. 양수는 추가, 음수는 차감
 * @param requestedDelta 관리자가 요청한 횟수. 차감은 남은 횟수까지만 하므로 {@code delta}보다 클 수 있다
 * @param reason         사유. 남기지 않았으면 {@code null}
 */
public record VolunteerAdjustmentResponse(
        @Schema(description = "조정한 시각") Instant createdAt,
        @Schema(description = "실제로 바뀐 횟수. 양수는 추가, 음수는 차감(당일 봉사 완료 포함)", example = "-2") int delta,
        @Schema(description = "관리자가 요청한 횟수. 차감은 남은 횟수까지만 하므로 delta와 다를 수 있다", example = "-5") int requestedDelta,
        @Schema(description = "사유. 남기지 않았으면 null", example = "청소 당번 대체") String reason
) {

    public static VolunteerAdjustmentResponse from(VolunteerAdjustment adjustment) {
        return new VolunteerAdjustmentResponse(
                adjustment.getCreatedAt(),
                adjustment.getDelta(),
                adjustment.getRequestedDelta(),
                adjustment.getReason());
    }
}
