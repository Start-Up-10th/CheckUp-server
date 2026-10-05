package com.checkup.checkup.domain.room.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 관리자 호실 수동 출석 저장 요청(REQ-ATT-006). 호실과 용도는 경로와 쿼리로 받는다.
 *
 * @param students 저장할 학생별 출석 상태. 호실 학생 전부를 보내도 되고 바꿀 학생만 보내도 된다
 */
public record RoomAttendanceRequest(
        @NotEmpty @Size(max = 50) @Valid List<Item> students
) {

    /**
     * 학생 한 명의 출석 상태.
     *
     * @param studentId DataGSM 학생 id. 호실 명단 응답의 {@code student_id}다
     * @param attended  출석이면 true, 미출석이면 false
     */
    public record Item(
            @NotNull Long studentId,
            @NotNull Boolean attended
    ) {
    }
}
