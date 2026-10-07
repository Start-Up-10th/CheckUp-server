package com.checkup.checkup.domain.face.service;

import com.checkup.checkup.domain.face.dto.FaceConsentResponse;
import com.checkup.checkup.domain.face.dto.FaceStatusResponse;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 로그인한 학생의 얼굴 정보 처리 동의 확인과 얼굴 등록 상태 조회를 처리한다. */
@Service
@RequiredArgsConstructor
public class FaceStudentService {
    private final FaceEnrollmentStore enrollmentStore;

    /**
     * 로그인한 학생이 필수 동의를 마쳤는지 확인하고 얼굴 동의 버전과 동의 시각을 돌려준다.
     * 동의 저장은 서비스 이용 동의 API가 하며, 여기서는 확인만 한다.
     *
     * @throws CustomException 학생이 아니면 {@link ErrorCode#MISSING_STUDENT_INFO}(403),
     *                         필수 동의가 없으면 {@link ErrorCode#FACE_CONSENT_REQUIRED}(403)
     */
    public FaceConsentResponse consent(Long memberId) {
        Student student = enrollmentStore.consent(memberId);
        return new FaceConsentResponse(true, student.getFaceConsentVersion(), student.getFaceAgreedAt());
    }

    /**
     * 로그인한 학생의 얼굴 등록 상태를 돌려준다. 등록했으면 {@code REGISTERED}, 아니면 {@code NOT_REGISTERED}다.
     *
     * @throws CustomException 학생이 아니면 {@link ErrorCode#MISSING_STUDENT_INFO}(403)
     */
    public FaceStatusResponse getStatus(Long memberId) {
        FaceEnrollmentStore.FaceStatusResponseData status = enrollmentStore.status(memberId);
        return new FaceStatusResponse(status.enrolled() ? "REGISTERED" : "NOT_REGISTERED",
                status.consented(), status.eligible(), status.enrolled());
    }
}
