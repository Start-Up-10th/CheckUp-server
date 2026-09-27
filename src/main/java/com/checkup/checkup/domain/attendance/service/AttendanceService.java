package com.checkup.checkup.domain.attendance.service;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.checkup.checkup.domain.attendance.entity.AttendanceMethod;
import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.attendance.repository.AttendanceRepository;

import lombok.RequiredArgsConstructor;

/**
 * 출석 기록을 담당한다. QR·얼굴 인식은 인증 성공 후 이 서비스로 출석을 확정한다.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;

    /**
     * 자동 인증 성공을 출석으로 기록한다. 동시에 여러 요청이 와도 한 번만 기록된다.
     *
     * @param studentId    출석할 학생 id
     * @param purpose      출석 용도
     * @param operatingDay 인증이 발생한 운영일
     * @param verifiedAt   인증이 발생한 시각
     * @param method       인증 방식
     * @return 새로 출석 처리됐으면 true, 이미 출석이었거나 무시됐으면 false
     */
    @Transactional
    public boolean markAttended(
            Long studentId,
            AttendancePurpose purpose,
            LocalDate operatingDay,
            Instant verifiedAt,
            AttendanceMethod method
    ) {
        return attendanceRepository.markAttended(
                studentId, purpose.name(), operatingDay, verifiedAt, method.name()) == 1;
    }
}
