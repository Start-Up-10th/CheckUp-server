package com.checkup.checkup.domain.face.service;

import com.checkup.checkup.domain.face.dto.FaceConsentResponse;
import com.checkup.checkup.domain.face.dto.FaceStatusResponse;
import com.checkup.checkup.domain.member.entity.Student;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FaceStudentService {
    private final FaceEnrollmentStore enrollmentStore;

    public FaceConsentResponse consent(Long memberId) {
        Student student = enrollmentStore.consent(memberId);
        return new FaceConsentResponse(true, student.getFaceConsentVersion(), student.getFaceAgreedAt());
    }

    public FaceStatusResponse getStatus(Long memberId) {
        FaceEnrollmentStore.FaceStatusResponseData status = enrollmentStore.status(memberId);
        return new FaceStatusResponse(status.enrolled() ? "REGISTERED" : "NOT_REGISTERED",
                status.consented(), status.eligible(), status.enrolled());
    }
}
