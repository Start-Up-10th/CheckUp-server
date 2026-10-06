package com.checkup.checkup.domain.room.controller;

import com.checkup.checkup.domain.room.dto.request.ManualRoomAttendanceRequest;
import com.checkup.checkup.domain.room.dto.response.ManualRoomAttendanceResponse;
import com.checkup.checkup.domain.room.dto.response.RoomAttendanceStudentResponse;
import com.checkup.checkup.domain.room.service.RoomMapService;
import io.swagger.v3.oas.annotations.Operation;
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

@Tag(name = "호실 전개도", description = "관리자 기숙사 출석 전개도 API")
@RestController
@RequestMapping("/api/v1/room")
@RequiredArgsConstructor
public class RoomMapController {

    private final RoomMapService roomMapService;

    @Operation(summary = "호실 출석 명단", description = "오늘 기숙사 출석 여부를 포함한 호실 학생 명단을 반환한다. student_id는 DataGSM 학생 ID다.")
    @GetMapping("/attendance")
    public List<RoomAttendanceStudentResponse> getRoomAttendance(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(name = "dormitoryRoom") @Positive Integer dormitoryRoom) {
        return roomMapService.getRoomAttendance(memberId, dormitoryRoom);
    }

    @Operation(summary = "호실 학생 수동 출석 수정", description = "오늘 운영일의 기숙사 출석 상태를 관리자가 지정한다. 경로 studentId는 DataGSM 학생 ID이며 attended가 현재 상태다.")
    @PutMapping("/student/{studentId}/attendance")
    public ManualRoomAttendanceResponse setManualAttendance(
            @AuthenticationPrincipal Long memberId,
            @PathVariable @Positive Long studentId,
            @Valid @RequestBody ManualRoomAttendanceRequest request) {
        return roomMapService.setManualAttendance(memberId, studentId, request.attended());
    }
}
