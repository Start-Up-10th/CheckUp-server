package com.checkup.checkup.domain.room.controller;

import com.checkup.checkup.domain.room.dto.request.ManualRoomAttendanceRequest;
import com.checkup.checkup.domain.room.dto.response.ManualRoomAttendanceResponse;
import com.checkup.checkup.domain.room.dto.response.RoomAttendanceStudentResponse;
import com.checkup.checkup.domain.room.service.RoomMapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 기숙사 출석 전개도에서 호실 학생 명단을 보고 학생 한 명씩 수동 출석을 수정한다. */
@Tag(name = "호실 전개도", description = "관리자 기숙사 출석 전개도 API")
@RestController
@RequestMapping("/api/v1/room")
@RequiredArgsConstructor
public class RoomMapController {

    private final RoomMapService roomMapService;

    /**
     * 한 호실의 학생과 오늘 운영일 기숙사 출석 여부를 이름 가나다순으로 돌려준다.
     *
     * @param memberId      세션의 회원 id
     * @param dormitoryRoom 호실 번호
     * @return 호실 출석 명단. DataGSM 학생 id가 없는 학생은 빠진다
     */
    @Operation(summary = "호실 출석 명단", description = "오늘 기숙사 출석 여부를 포함한 호실 학생 명단을 반환한다. student_id는 DataGSM 학생 ID다.")
    @GetMapping("/attendance")
    public List<RoomAttendanceStudentResponse> getRoomAttendance(
            @AuthenticationPrincipal Long memberId,
            @Parameter(description = "호실 번호", example = "301") @RequestParam(name = "dormitoryRoom") @Positive Integer dormitoryRoom) {
        return roomMapService.getRoomAttendance(memberId, dormitoryRoom);
    }

    /**
     * 학생 한 명의 오늘 운영일 기숙사 출석 상태를 관리자가 지정한다.
     *
     * @param memberId  세션의 회원 id
     * @param studentId DataGSM 학생 id
     * @param request   지정할 출석 상태
     * @return 수정 뒤 출석 상태
     */
    @Operation(summary = "호실 학생 수동 출석 수정", description = "오늘 운영일의 기숙사 출석 상태를 관리자가 지정한다. 경로 studentId는 DataGSM 학생 ID이며 attended가 현재 상태다.")
    @PutMapping("/student/{studentId}/attendance")
    public ManualRoomAttendanceResponse setManualAttendance(
            @AuthenticationPrincipal Long memberId,
            @Parameter(description = "DataGSM 학생 id. DB id가 아니다", example = "1") @PathVariable @Positive Long studentId,
            @Valid @RequestBody ManualRoomAttendanceRequest request) {
        return roomMapService.setManualAttendance(memberId, studentId, request.attended());
    }
}
