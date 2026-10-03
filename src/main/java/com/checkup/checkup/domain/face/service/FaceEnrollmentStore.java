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

/** Owns short consent and template database transactions; AI network calls happen outside. */
@Service
@RequiredArgsConstructor
public class FaceEnrollmentStore {
    private final StudentRepository studentRepository;
    private final FaceTemplateRepository faceTemplateRepository;

    @Transactional(readOnly = true)
    public Student consent(Long memberId) {
        Student student = findStudent(memberId);
        if (!student.hasRequiredConsent()) {
            throw new CustomException(ErrorCode.FACE_CONSENT_REQUIRED);
        }
        return student;
    }

    @Transactional(readOnly = true)
    public FaceStatusResponseData status(Long memberId) {
        Student student = findStudent(memberId);
        return new FaceStatusResponseData(student.hasRequiredConsent(), isEligible(student),
                faceTemplateRepository.existsByStudent_Id(student.getId()));
    }

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

    public record FaceStatusResponseData(boolean consented, boolean eligible, boolean enrolled) {
    }
}
