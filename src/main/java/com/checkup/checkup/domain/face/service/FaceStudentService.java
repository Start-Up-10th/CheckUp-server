package com.checkup.checkup.domain.face.service;

import com.checkup.checkup.domain.face.config.FaceProperties;
import com.checkup.checkup.domain.face.dto.FaceConsentResponse;
import com.checkup.checkup.domain.face.dto.FaceStatusResponse;
import com.checkup.checkup.domain.member.entity.Student;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class FaceStudentService {
    private final FaceEnrollmentStore enrollmentStore;
    private final FaceProperties properties;
    private final Clock clock;

    public FaceConsentResponse grantConsent(Long memberId) {
        Student student = enrollmentStore.grantConsent(memberId, properties.consentVersion(), clock.instant());
        return new FaceConsentResponse(true, student.getFaceConsentVersion(), student.getFaceConsentAt());
    }

    public FaceStatusResponse getStatus(Long memberId) {
        FaceEnrollmentStore.FaceStatusResponseData status = enrollmentStore.status(memberId);
        return new FaceStatusResponse(status.consented(), status.enrolled());
    }
}
