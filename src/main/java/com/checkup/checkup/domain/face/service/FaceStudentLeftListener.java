package com.checkup.checkup.domain.face.service;

import com.checkup.checkup.domain.member.dto.StudentLeftEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** DataGSM이 학생의 졸업·자퇴를 알려 오면 저장된 얼굴 등록 정보와 AI 메모리의 후보를 지운다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class FaceStudentLeftListener {
    private final FaceEnrollmentStore enrollmentStore;
    private final FaceRecognitionService faceRecognitionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStudentLeft(StudentLeftEvent event) {
        try {
            enrollmentStore.deleteTemplate(event.studentId());
        } catch (RuntimeException e) {
            log.warn("Face-template cleanup failed after student departure: reason={}",
                    e.getClass().getSimpleName());
        }
        try {
            faceRecognitionService.removeStudent(event.studentId());
        } catch (RuntimeException e) {
            log.warn("Face-session cleanup after student departure failed: reason={}",
                    e.getClass().getSimpleName());
        }
    }
}
