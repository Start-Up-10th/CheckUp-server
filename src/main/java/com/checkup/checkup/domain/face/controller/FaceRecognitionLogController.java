package com.checkup.checkup.domain.face.controller;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.face.dto.FaceRecognitionLogResponse;
import com.checkup.checkup.domain.face.service.FaceRecognitionLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 관리자 얼굴 출석 화면의 최근 인식 목록(#143). */
@Tag(name = "얼굴 인식(관리자 카메라)")
@RestController
@RequestMapping("/api/v1/face/recognitions")
@RequiredArgsConstructor
public class FaceRecognitionLogController {
    private final FaceRecognitionLogService faceRecognitionLogService;

    @Operation(summary = "최근 인식 목록", description = "관리자 전용. 로그인한 관리자가 연 세션의 오늘(운영일) 기록을 최신순으로 돌려준다. 이미 출석한 학생은 남기지 않고, 못 알아본 얼굴은 QR 안내 때만 이름 없이 FAILED로 남는다. limit은 1~100(기본 50), 벗어나면 400 INVALID_REQUEST. 지난 운영일 기록은 08:00 KST에 지운다.")
    @GetMapping
    public List<FaceRecognitionLogResponse> getRecent(
            @AuthenticationPrincipal Long memberId,
            @Parameter(description = "출석 용도. DORMITORY 또는 STUDY_ROOM", example = "DORMITORY") @RequestParam AttendancePurpose purpose,
            @Parameter(description = "최대 개수(선택, 1~100). 없으면 50", example = "50") @RequestParam(required = false) Integer limit
    ) {
        return faceRecognitionLogService.getRecent(memberId, purpose, limit);
    }
}
