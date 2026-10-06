package com.checkup.checkup.domain.room.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.room.dto.request.RoomAttendanceRequest;
import com.checkup.checkup.domain.room.dto.response.RoomFloorResponse;
import com.checkup.checkup.domain.room.dto.response.RoomStudentResponse;
import com.checkup.checkup.domain.room.service.RoomService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 호실 학생 명단·층 단위 출석 현황 조회와 호실 수동 출석 저장 API. */
@Tag(name = "호실", description = "호실 학생 명단, 층 단위 출석 현황, 수동 출석 저장")
@RestController
@RequestMapping("/api/v1/room")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    /**
     * 관리자는 모든 호실을, 학생은 본인 호실의 학생 명단과 오늘 출석 여부를 조회한다.
     *
     * @param memberId 세션의 현재 회원 id
     * @param dormitoryRoom 호실 번호
     * @param purpose 출석 여부를 볼 용도. 없으면 기숙사 입소(DORMITORY)다
     * @return 해당 호실의 학생 명단
     */
    @Operation(summary = "호실 학생 명단", description = "관리자는 모든 호실, 학생은 본인 호실만 조회할 수 있다. attended는 purpose(기본 DORMITORY)의 오늘 운영일(08:00 KST 기준) 출석 여부다. 학생이 없는 호실은 빈 배열이다.")
    @GetMapping("/student")
    public List<RoomStudentResponse> getStudents(
            @AuthenticationPrincipal Long memberId,
            @Parameter(description = "호실 번호", example = "301") @RequestParam(name = "dormitoryRoom") @Positive Integer dormitoryRoom,
            @Parameter(description = "출석 용도. DORMITORY(기숙사 입소) 또는 STUDY_ROOM(자습실)", example = "DORMITORY") @RequestParam(name = "purpose", defaultValue = "DORMITORY") AttendancePurpose purpose) {
        return roomService.getStudents(memberId, dormitoryRoom, purpose);
    }

    /**
     * 관리자가 전개도에 쓸 한 층의 호실별 배정 인원과 오늘 출석 인원을 조회한다.
     *
     * @param memberId 세션의 현재 회원 id
     * @param floor 층. 호실 번호의 백의 자리 이상이다(예: 301호는 3층)
     * @param purpose 출석 용도. 없으면 기숙사 입소(DORMITORY)다
     * @return 층 전체 출석·미출석 인원과 호실별 현황
     */
    @Operation(summary = "층 단위 출석 현황", description = "관리자만 조회할 수 있다. 한 층의 호실마다 배정 인원(assigned)과 purpose(기본 DORMITORY)의 오늘 운영일(08:00 KST 기준) 출석 인원(attended)을 호실 번호 오름차순으로 준다. 층 전체 출석·미출석 인원도 함께 준다. 배정된 학생이 없는 층은 rooms가 빈 배열이다.")
    @GetMapping("/floor")
    public RoomFloorResponse getFloor(
            @AuthenticationPrincipal Long memberId,
            @Parameter(description = "층(호실 번호를 100으로 나눈 값)", example = "3") @RequestParam(name = "floor") @Positive @Max(99) Integer floor,
            @Parameter(description = "출석 용도. DORMITORY(기숙사 입소) 또는 STUDY_ROOM(자습실)", example = "DORMITORY") @RequestParam(name = "purpose", defaultValue = "DORMITORY") AttendancePurpose purpose) {
        return roomService.getFloor(memberId, floor, purpose);
    }

    /**
     * 관리자가 호실 상세에서 고른 학생별 출석·미출석을 오늘 운영일에 저장한다.
     *
     * @param memberId 세션의 현재 회원 id
     * @param dormitoryRoom 호실 번호
     * @param purpose 출석 용도. 없으면 기숙사 입소(DORMITORY)다
     * @param request 학생별 출석 상태
     * @return 본문 없는 204 응답
     */
    @Operation(summary = "호실 수동 출석 저장", description = "관리자만 저장할 수 있다. students의 studentId는 호실 명단의 student_id(DataGSM 학생 id)다. purpose(기본 DORMITORY)의 오늘 운영일(08:00 KST 기준) 출석 상태를 바꾸며, 이미 같은 상태인 학생은 그대로 둔다. 그 호실 학생이 아닌 학생이 있으면 400 STUDENT_NOT_IN_ROOM이고 아무것도 저장하지 않는다. 성공하면 204다.")
    @PutMapping("/{dormitoryRoom}/attendance")
    public ResponseEntity<Void> saveAttendance(
            @AuthenticationPrincipal Long memberId,
            @Parameter(description = "출석을 저장할 호실 번호", example = "301") @PathVariable @Positive Integer dormitoryRoom,
            @Parameter(description = "출석 용도. DORMITORY(기숙사 입소) 또는 STUDY_ROOM(자습실)", example = "DORMITORY") @RequestParam(name = "purpose", defaultValue = "DORMITORY") AttendancePurpose purpose,
            @Valid @RequestBody RoomAttendanceRequest request) {
        roomService.saveAttendance(memberId, dormitoryRoom, purpose, request);
        return ResponseEntity.noContent().build();
    }
}
