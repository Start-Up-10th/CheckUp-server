package com.checkup.checkup.domain.user.dto.Response;

import com.checkup.checkup.domain.volunteer.entity.VolunteerDuty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 학생의 봉사 완료 내역 응답. 자치위원이 완료를 확인한 당일 봉사만 담고, 최신 운영일부터 정렬한다.
 * 완료 한 건이 봉사 1회다(완료하면 남은 봉사 횟수가 1 줄어든다, DEC-021).
 *
 * @param studentId DataGSM 학생 id
 * @param history   완료한 봉사 목록
 */
public record UserVolunteerHistoryResponse(
        @Schema(description = "DataGSM 학생 id", example = "1") Long studentId,
        @Schema(description = "완료한 봉사 목록. 최신 운영일부터, 한 건이 봉사 1회") List<Item> history
) {

    /**
     * 완료한 봉사 한 건.
     *
     * @param operatingDay 봉사한 운영일(08:00 KST 기준 날짜)
     * @param completedAt  완료를 확인한 시각(UTC)
     */
    public record Item(
            @Schema(description = "봉사한 운영일(08:00 KST 기준 날짜)", example = "2026-10-06") LocalDate operatingDay,
            @Schema(description = "완료를 확인한 시각(UTC)", example = "2026-10-06T12:00:00Z") Instant completedAt
    ) {

        public static Item from(VolunteerDuty duty) {
            return new Item(duty.getOperatingDay(), duty.getCompletedAt());
        }
    }
}
