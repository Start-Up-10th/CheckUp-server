package com.checkup.checkup.domain.face.controller;

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

    @PostMapping(value = "/enrollments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FaceEnrollmentResponse enroll(
            @AuthenticationPrincipal Long memberId,
            @RequestPart("video") MultipartFile video
    ) {
        return faceEnrollmentService.enroll(memberId, video);
    }
}
