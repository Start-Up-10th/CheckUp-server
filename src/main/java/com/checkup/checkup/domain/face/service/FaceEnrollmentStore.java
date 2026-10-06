package com.checkup.checkup.domain.face.service;

import com.checkup.checkup.domain.face.ai.AiFaceModel;
import com.checkup.checkup.domain.face.entity.FaceTemplate;
import com.checkup.checkup.domain.face.repository.FaceTemplateRepository;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 동의 확인과 템플릿 저장의 짧은 DB 트랜잭션을 맡는다. AI 네트워크 호출은 트랜잭션 밖에서 한다. */
@Service
@RequiredArgsConstructor
public class FaceEnrollmentStore {
    private final StudentRepository studentRepository;
    private final FaceTemplateRepository faceTemplateRepository;

    /**
     * 로그인한 학생이 필수 동의를 마쳤는지 확인하고 학생을 돌려준다. 얼굴 등록 전에 AI를 부르기 전에 확인한다.
     *
     * @throws CustomException 학생이 아니면 {@link ErrorCode#MISSING_STUDENT_INFO}(403),
     *                         필수 동의가 없으면 {@link ErrorCode#FACE_CONSENT_REQUIRED}(403)
     */
    @Transactional(readOnly = true)
    public Student consent(Long memberId) {
        Student student = findStudent(memberId);
        if (!student.hasRequiredConsent()) {
            throw new CustomException(ErrorCode.FACE_CONSENT_REQUIRED);
        }
        return student;
    }

    /**
     * 로그인한 학생의 필수 동의, 등록 대상 여부(DataGSM 학생 id와 호실이 있는지), 얼굴 등록 여부를 읽는다.
     *
     * @throws CustomException 학생이 아니면 {@link ErrorCode#MISSING_STUDENT_INFO}(403)
     */
    @Transactional(readOnly = true)
    public FaceStatusResponseData status(Long memberId) {
        Student student = findStudent(memberId);
        return new FaceStatusResponseData(student.hasRequiredConsent(), isEligible(student),
                faceTemplateRepository.existsByStudent_Id(student.getId()));
    }

    /**
     * AI가 뽑은 대표 벡터를 학생의 얼굴 템플릿으로 저장한다. 등록은 한 번만 할 수 있다.
     *
     * @param vectorsJson 대표 벡터 목록 JSON. 로그에 남기지 않는다
     * @throws CustomException 학생이 아니면 {@link ErrorCode#MISSING_STUDENT_INFO}(403),
     *                         필수 동의가 없으면 {@link ErrorCode#FACE_CONSENT_REQUIRED}(403),
     *                         DataGSM 학생 id나 호실이 없으면 {@link ErrorCode#FACE_ENROLLMENT_NOT_ELIGIBLE}(403),
     *                         이미 등록했으면 {@link ErrorCode#FACE_ALREADY_REGISTERED}
     */
    @Transactional
    public void saveTemplate(Long memberId, AiFaceModel model, String vectorsJson) {
        Student student = findStudent(memberId);
        if (!student.hasRequiredConsent()) {
            throw new CustomException(ErrorCode.FACE_CONSENT_REQUIRED);
        }
        if (!isEligible(student)) {
            throw new CustomException(ErrorCode.FACE_ENROLLMENT_NOT_ELIGIBLE);
        }
        if (faceTemplateRepository.existsByStudent_Id(student.getId())) {
            throw new CustomException(ErrorCode.FACE_ALREADY_REGISTERED);
        }
        faceTemplateRepository.save(FaceTemplate.create(student, model, vectorsJson));
    }

    /** 학생의 얼굴 템플릿을 지운다. 졸업·자퇴 처리에서 쓰며, 호출한 쪽 트랜잭션과 상관없이 지우도록 새 트랜잭션에서 한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteTemplate(Long studentId) {
        faceTemplateRepository.deleteByStudent_Id(studentId);
    }

    private Student findStudent(Long memberId) {
        return studentRepository.findByMemberId(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MISSING_STUDENT_INFO));
    }

    private static boolean isEligible(Student student) {
        return student.getDatagsmStudentId() != null && student.getDatagsmStudentId() > 0
                && student.getDormitoryRoom() != null;
    }

    /**
     * 얼굴 등록 상태.
     *
     * @param consented 필수 동의를 마쳤는지
     * @param eligible  등록 대상인지(DataGSM 학생 id와 호실이 있는지)
     * @param enrolled  얼굴을 등록했는지
     */
    public record FaceStatusResponseData(boolean consented, boolean eligible, boolean enrolled) {
    }
}
