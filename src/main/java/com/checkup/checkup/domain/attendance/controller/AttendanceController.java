package com.checkup.checkup.domain.attendance.controller;

import com.checkup.checkup.domain.attendance.dto.response.AttendanceActiveResponse;
import com.checkup.checkup.domain.attendance.dto.response.MyAttendanceResponse;
import com.checkup.checkup.domain.attendance.service.AttendanceActiveService;
import com.checkup.checkup.domain.attendance.service.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학생 본인의 출석 조회 API. 로그인한 학생의 출석만 다루며 다른 학생 id는 받지 않는다.
 * 지금 출석을 받는 중인지도 여기서 알려 준다.
 */
@Tag(name = "출석", description = "학생 본인의 오늘 출석 상태와 지금 출석을 받는 중인지")
@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AttendanceActiveService attendanceActiveService;

    /**
     * 본인의 오늘 운영일 출석 상태를 기숙사 입소·자습실로 나눠 조회한다.
     *
     * @param memberId 세션의 회원 id
     */
    @Operation(summary = "내 오늘 출석 상태", description = "오늘 운영일(08:00 KST 기준)의 기숙사 입소(dormitory)·자습실(studyRoom) 출석 상태를 준다. 기록이 없으면 미출석이다. firstVerifiedAt은 출석 상태일 때의 최초 인증 시각이며, 관리자가 수동으로만 출석 처리했으면 null이다. 학생이 아니면 403 MISSING_STUDENT_INFO.")
    @GetMapping("/me")
    public MyAttendanceResponse getMyAttendance(@AuthenticationPrincipal Long memberId) {
        return attendanceService.getMyToday(memberId);
    }

    /**
     * 지금 용도별로 출석을 받는 중인지 조회한다.
     *
     * @param memberId 세션의 회원 id
     */
    @Operation(summary = "지금 출석을 받는 중인지", description = "용도별로 지금 출석을 받는 세션이 하나라도 있으면 true다. QR은 그 용도의 살아 있는 QR 세션, 얼굴은 그 용도로 학생 본인 층을 맡은 활성 얼굴 인식 세션을 센다. 호실이 없는 학생은 얼굴 세션을 세지 않는다. 세션 id·관리자·토큰은 주지 않는다. 출석 시간표는 없다. 학생이 아니면 403 MISSING_STUDENT_INFO.")
    @GetMapping("/active")
    public AttendanceActiveResponse getActive(@AuthenticationPrincipal Long memberId) {
        return attendanceActiveService.getActive(memberId);
    }
}
