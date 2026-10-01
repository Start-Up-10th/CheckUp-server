package com.checkup.checkup.domain.face.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.checkup.checkup.domain.face.dto.FaceConsentResponse;
import com.checkup.checkup.domain.face.dto.FaceEnrollmentResponse;
import com.checkup.checkup.domain.face.dto.FaceStatusResponse;
import com.checkup.checkup.domain.face.service.FaceEnrollmentService;
import com.checkup.checkup.domain.face.service.FaceStudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Student face-consent, status, and first-enrollment endpoints. */
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

    /** Returns the face consent already recorded through the required consent flow. */
    @Operation(summary = "얼굴 정보 처리 동의 확인", description = "필수 동의 흐름에서 이미 기록한 얼굴 동의를 돌려준다.")
    @PostMapping("/consent")
    @ResponseStatus(HttpStatus.CREATED)
    public FaceConsentResponse consent(@AuthenticationPrincipal Long memberId) {
        return faceStudentService.consent(memberId);
    }

    @Operation(summary = "얼굴 최초 등록", description = "multipart/form-data의 video 파트로 등록 영상 하나를 보낸다.")
    @PostMapping(value = "/enrollments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FaceEnrollmentResponse enroll(
            @AuthenticationPrincipal Long memberId,
            @RequestPart("video") MultipartFile video
    ) {
        return faceEnrollmentService.enroll(memberId, video);
    }
}
