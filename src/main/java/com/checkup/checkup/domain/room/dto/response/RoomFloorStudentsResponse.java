package com.checkup.checkup.domain.room.dto.response;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 관리자 전개도에서 한 층의 호실별 학생 이름과 오늘 출석 상태를 조회하는 응답(REQ-UI-001). */
public record RoomFloorStudentsResponse(
        @Schema(description = "층", example = "3") int floor,
        @Schema(description = "출석 용도", example = "DORMITORY") AttendancePurpose purpose,
        @Schema(description = "호실 번호 오름차순의 학생 명단. 학생이 없는 층은 빈 배열") List<Room> rooms
) {
    /** 호실 상세와 같은 필드와 이름·학번·내부 id 순으로 정렬된 학생 명단. */
    public record Room(
            @Schema(description = "호실 번호", example = "301") int dormitoryRoom,
            @Schema(description = "DataGSM 학생 id가 있는 학생 명단. 이름·학번·내부 id 순")
            List<RoomAttendanceStudentResponse> students
    ) {
    }
}
