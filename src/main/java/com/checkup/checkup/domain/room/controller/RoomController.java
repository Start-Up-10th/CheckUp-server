package com.checkup.checkup.domain.room.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.checkup.checkup.domain.room.dto.response.RoomStudentResponse;
import com.checkup.checkup.domain.room.service.RoomService;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 호실 학생 명단 조회 API. */
@Tag(name = "호실", description = "호실 학생 명단")
@RestController
@RequestMapping("/api/v1/room")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    /**
     * 관리자는 모든 호실을, 학생은 본인 호실의 학생 명단을 조회한다.
     *
     * @param memberId 세션의 현재 회원 id
     * @param dormitoryRoom 호실 번호
     * @return 해당 호실의 학생 명단
     */
    @Operation(summary = "호실 학생 명단", description = "관리자는 모든 호실, 학생은 본인 호실만 조회할 수 있다.")
    @GetMapping("/student")
    public List<RoomStudentResponse> getStudents(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(name = "dormitoryRoom") @Positive Integer dormitoryRoom) {
        return roomService.getStudents(memberId, dormitoryRoom);
    }
}
