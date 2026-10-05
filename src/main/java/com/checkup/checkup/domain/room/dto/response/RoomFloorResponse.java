package com.checkup.checkup.domain.room.dto.response;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.attendance.repository.RoomAttendanceCount;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 관리자 전개도의 층 단위 출석 현황(REQ-UI-001).
 *
 * @param floor    층
 * @param purpose  출석 용도
 * @param attended 층 전체에서 지금 출석 상태인 학생 수
 * @param absent   층 전체에서 출석하지 않은 학생 수
 * @param rooms    호실 번호 오름차순의 호실별 현황. 배정된 학생이 없는 호실은 없다
 */
public record RoomFloorResponse(
        @Schema(description = "층", example = "3") int floor,
        @Schema(description = "출석 용도. DORMITORY 또는 STUDY_ROOM", example = "DORMITORY") AttendancePurpose purpose,
        @Schema(description = "층 전체에서 지금 출석 상태인 학생 수", example = "40") long attended,
        @Schema(description = "층 전체에서 출석하지 않은 학생 수", example = "44") long absent,
        @Schema(description = "호실 번호 오름차순의 호실별 현황. 배정된 학생이 없는 호실은 없다") List<Room> rooms
) {

    /**
     * 호실 카드 하나.
     *
     * @param dormitoryRoom 호실 번호
     * @param attended      지금 출석 상태인 학생 수
     * @param assigned      배정된 학생 수
     */
    public record Room(
            @Schema(description = "호실 번호", example = "301") int dormitoryRoom,
            @Schema(description = "지금 출석 상태인 학생 수", example = "3") long attended,
            @Schema(description = "배정된 학생 수(정원 고정 아님)", example = "4") long assigned
    ) {
    }

    public static RoomFloorResponse of(int floor, AttendancePurpose purpose, List<RoomAttendanceCount> counts) {
        List<Room> rooms = counts.stream()
                .map(count -> new Room(count.getDormitoryRoom(), count.getAttended(), count.getAssigned()))
                .toList();
        long attended = rooms.stream().mapToLong(Room::attended).sum();
        long assigned = rooms.stream().mapToLong(Room::assigned).sum();
        return new RoomFloorResponse(floor, purpose, attended, assigned - attended, rooms);
    }
}
