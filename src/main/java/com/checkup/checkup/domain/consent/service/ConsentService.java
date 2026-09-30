package com.checkup.checkup.domain.consent.service;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;

/**
 * 학생의 서비스 이용 동의를 저장하고 조회한다(REQ-AUTH-004).
 *
 * 필수 두 항목(개인정보, 얼굴 정보)과 선택 항목(기숙사 공지 알림 수신)을 학생에 기록한다.
 * 동의할 학생은 요청 값이 아니라 로그인 세션의 회원으로 정한다.
 */
@Service
public class ConsentService {

    private final StudentRepository studentRepository;
    private final Clock clock;
    private final String faceConsentVersion;

    public ConsentService(
            StudentRepository studentRepository,
            Clock clock,
            @Value("${checkup.face.consent-version}") String faceConsentVersion) {
        this.studentRepository = studentRepository;
        this.clock = clock;
        this.faceConsentVersion = faceConsentVersion;
    }

    /**
     * 현재 학생의 필수 동의를 기록하고 공지 알림 수신 여부를 정한다.
     *
     * @param memberId    로그인 세션의 회원 id
     * @param noticeAlarm 기숙사 공지 알림 수신 여부
     * @throws CustomException 학생이 아닌 회원이면 {@link ErrorCode#MISSING_STUDENT_INFO}(403)
     */
    @Transactional
    public void agree(Long memberId, boolean noticeAlarm) {
        Student student = studentRepository.findByMemberId(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MISSING_STUDENT_INFO));
        student.agree(noticeAlarm, clock.instant(), faceConsentVersion);
    }

    /**
     * 현재 회원이 필수 동의를 마쳤는지 확인한다. 학생이 아닌 회원(교사)은 동의 흐름이 없어 false다.
     *
     * @param memberId 로그인 세션의 회원 id
     */
    @Transactional(readOnly = true)
    public boolean hasRequiredConsent(Long memberId) {
        return studentRepository.findByMemberId(memberId)
                .map(Student::hasRequiredConsent)
                .orElse(false);
    }
}
