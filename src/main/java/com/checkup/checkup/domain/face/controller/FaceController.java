package com.checkup.checkup.domain.face.controller;

import com.checkup.checkup.domain.face.dto.FaceConsentResponse;
import com.checkup.checkup.domain.face.dto.FaceEnrollmentResponse;
import com.checkup.checkup.domain.face.dto.FaceStatusResponse;
import com.checkup.checkup.domain.face.service.FaceEnrollmentService;
import com.checkup.checkup.domain.face.service.FaceStudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

/** 학생 얼굴 동의, 등록 상태 조회 및 최초 등록 API를 제공한다. */
@Tag(name = "얼굴 등록(학생)", description = "학생 얼굴 동의 확인·등록 상태·최초 등록")
@RestController
@RequestMapping("/api/v1/face")
@RequiredArgsConstructor
public class FaceController {
    private final FaceStudentService faceStudentService;
    private final FaceEnrollmentService faceEnrollmentService;

    @Operation(summary = "내 얼굴 등록 상태 조회")
    @GetMapping("/me")
    public FaceStatusResponse me(@AuthenticationPrincipal Long memberId) {
        return faceStudentService.getStatus(memberId);
    }

    /** 필수 동의 흐름에서 기록한 얼굴 정보 처리 동의를 반환한다. */
    @Operation(summary = "얼굴 정보 처리 동의 확인", description = "필수 동의 흐름에서 이미 기록한 얼굴 동의를 돌려준다.")
    @PostMapping("/consent")
    @ResponseStatus(HttpStatus.CREATED)
    public FaceConsentResponse consent(@AuthenticationPrincipal Long memberId) {
        return faceStudentService.consent(memberId);
    }

    /** 원본 영상 바이트만 받고, 모든 응답 경로에서 업로드 버퍼를 초기화한다. */
    @Operation(
            summary = "얼굴 최초 등록",
            description = "video/webm 또는 video/mp4 영상 원본 바이트를 요청 본문으로 보낸다."
    )
    @PostMapping(value = "/enrollments", consumes = {"video/webm", "video/mp4"})
    @ResponseStatus(HttpStatus.CREATED)
    public FaceEnrollmentResponse enroll(
            @AuthenticationPrincipal Long memberId,
            @RequestHeader("Content-Type") String contentType,
            @RequestBody byte[] video
    ) {
        try {
            return faceEnrollmentService.enroll(memberId, contentType, video);
        } finally {
            if (video != null) {
                Arrays.fill(video, (byte) 0);
            }
        }
    }
}
