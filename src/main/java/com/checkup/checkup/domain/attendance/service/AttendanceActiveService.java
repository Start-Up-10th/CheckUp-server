package com.checkup.checkup.domain.attendance.service;

import org.springframework.stereotype.Service;

import com.checkup.checkup.domain.attendance.dto.response.AttendanceActiveResponse;
import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.face.service.FaceRecognitionService;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.qr.service.QrSessionService;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 학생에게 지금 출석을 받는 중인지 알려 준다(#260). 출석에는 시간표가 없고 관리자가 QR·얼굴 인식 페이지를 열어 둔 동안만 받는다(REQ-ATT-001).
 *
 * - QR: 그 용도의 살아 있는 QR 세션이 하나라도 있으면 받는 중이다. QR은 층을 가리지 않는다.
 * - 얼굴: 그 용도로 학생 본인 층을 맡은 활성 얼굴 인식 세션이 있어야 한다. 다른 층 카메라는 이 학생을 인식하지 않는다(#256).
 *
 * {@link AttendanceService}는 얼굴 인식 서비스가 쓰므로, 순환 의존을 피하려고 따로 둔다.
 */
@Service
@RequiredArgsConstructor
public class AttendanceActiveService {

    private final StudentRepository studentRepository;
    private final QrSessionService qrSessionService;
    private final FaceRecognitionService faceRecognitionService;

    /**
     * 로그인한 학생 기준으로 용도별 출석 접수 여부를 조회한다. 학생은 세션의 회원으로 정한다.
     *
     * @param memberId 세션의 회원 id
     * @throws CustomException 학생이 아니면 {@link ErrorCode#MISSING_STUDENT_INFO}(403)
     */
    public AttendanceActiveResponse getActive(Long memberId) {
        Student student = studentRepository.findByMemberId(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MISSING_STUDENT_INFO));
        Integer floor = student.getDormitoryFloor();
        return new AttendanceActiveResponse(
                isAccepting(AttendancePurpose.DORMITORY, floor),
                isAccepting(AttendancePurpose.STUDY_ROOM, floor));
    }

    private boolean isAccepting(AttendancePurpose purpose, Integer floor) {
        return qrSessionService.isAccepting(purpose) || faceRecognitionService.isAccepting(purpose, floor);
    }
}
