package com.checkup.checkup.domain.face.controller;

import com.checkup.checkup.domain.face.dto.FaceConsentResponse;
import com.checkup.checkup.domain.face.dto.FaceEnrollmentResponse;
import com.checkup.checkup.domain.face.dto.FaceStatusResponse;
import com.checkup.checkup.domain.face.service.FaceEnrollmentService;
import com.checkup.checkup.domain.face.service.FaceStudentService;
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

/** Student face-consent, status, and first-enrollment endpoints. */
@RestController
@RequestMapping("/api/v1/face")
@RequiredArgsConstructor
public class FaceController {
    private final FaceStudentService faceStudentService;
    private final FaceEnrollmentService faceEnrollmentService;

    @GetMapping("/me")
    public FaceStatusResponse me(@AuthenticationPrincipal Long memberId) {
        return faceStudentService.getStatus(memberId);
    }

    /** Returns the face consent already recorded through the required consent flow. */
    @PostMapping("/consent")
    @ResponseStatus(HttpStatus.CREATED)
    public FaceConsentResponse consent(@AuthenticationPrincipal Long memberId) {
        return faceStudentService.consent(memberId);
    }

    /** 원본 영상 바이트만 받고, 모든 응답 경로에서 업로드 버퍼를 초기화한다. */
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
