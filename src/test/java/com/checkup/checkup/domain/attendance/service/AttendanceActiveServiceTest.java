package com.checkup.checkup.domain.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.checkup.checkup.domain.attendance.dto.response.AttendanceActiveResponse;
import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.face.service.FaceRecognitionService;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.qr.service.QrSessionService;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;

/**
 * 용도별 출석 접수 여부가 QR 세션 또는 학생 본인 층의 얼굴 인식 세션으로 정해지는지 검증한다(REQ-ATT-001, #260).
 */
class AttendanceActiveServiceTest {

    private static final Long MEMBER_ID = 1L;

    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final QrSessionService qrSessionService = mock(QrSessionService.class);
    private final FaceRecognitionService faceRecognitionService = mock(FaceRecognitionService.class);
    private final AttendanceActiveService service =
            new AttendanceActiveService(studentRepository, qrSessionService, faceRecognitionService);

    @Test
    @DisplayName("QR 세션이 열린 용도는 층과 상관없이 출석을 받는 중이다")
    void qrSessionMakesPurposeActive() {
        givenStudentOnFloor(3);
        given(qrSessionService.isAccepting(AttendancePurpose.DORMITORY)).willReturn(true);

        AttendanceActiveResponse response = service.getActive(MEMBER_ID);

        assertThat(response).isEqualTo(new AttendanceActiveResponse(true, false));
    }

    @Test
    @DisplayName("본인 층 얼굴 인식 세션이 열린 용도는 출석을 받는 중이다")
    void faceSessionOnOwnFloorMakesPurposeActive() {
        givenStudentOnFloor(4);
        given(faceRecognitionService.isAccepting(AttendancePurpose.STUDY_ROOM, 4)).willReturn(true);

        AttendanceActiveResponse response = service.getActive(MEMBER_ID);

        assertThat(response).isEqualTo(new AttendanceActiveResponse(false, true));
    }

    @Test
    @DisplayName("열린 세션이 없으면 두 용도 모두 받지 않는다")
    void noSessionMeansInactive() {
        givenStudentOnFloor(5);

        assertThat(service.getActive(MEMBER_ID)).isEqualTo(new AttendanceActiveResponse(false, false));
    }

    @Test
    @DisplayName("학생이 아니면 MISSING_STUDENT_INFO로 거부하고 세션을 보지 않는다")
    void nonStudentIsRejected() {
        given(studentRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActive(MEMBER_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MISSING_STUDENT_INFO));
        verifyNoInteractions(qrSessionService, faceRecognitionService);
    }

    private void givenStudentOnFloor(int floor) {
        Student student = mock(Student.class);
        given(student.getDormitoryFloor()).willReturn(floor);
        given(studentRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(student));
    }
}
