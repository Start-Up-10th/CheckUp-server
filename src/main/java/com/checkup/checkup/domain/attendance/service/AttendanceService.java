package com.checkup.checkup.domain.attendance.service;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.checkup.checkup.domain.attendance.entity.Attendance;
import com.checkup.checkup.domain.attendance.entity.AttendanceMethod;
import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.attendance.entity.AttendanceRecordResult;
import com.checkup.checkup.domain.attendance.repository.AttendanceRepository;
import com.checkup.checkup.global.time.OperatingDayCalculator;

import lombok.RequiredArgsConstructor;

/**
 * 출석 기록을 담당한다. QR·얼굴 인식은 인증 성공 후 이 서비스로 출석을 확정한다.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final OperatingDayCalculator operatingDayCalculator;

    /**
     * 자동 인증 성공을 출석으로 기록한다. 동시에 여러 요청이 와도 한 번만 기록된다.
     *
     * <p>운영일은 호출하는 쪽이 넘기지 않고 인증 시각으로 계산한다. 그 운영일이 오늘이 아니면
     * 늦게 도착한 지난 인증이라 기록하지 않는다(REQ-ATT-007).
     *
     * @param studentId  출석할 학생 id
     * @param purpose    출석 용도
     * @param verifiedAt 인증이 발생한 시각
     * @param method     인증 방식
     * @return 기록 결과
     */
    @Transactional
    public AttendanceRecordResult markAttended(
            Long studentId,
            AttendancePurpose purpose,
            Instant verifiedAt,
            AttendanceMethod method
    ) {
        LocalDate operatingDay = operatingDayCalculator.of(verifiedAt);
        if (!operatingDay.equals(operatingDayCalculator.today())) {
            return AttendanceRecordResult.STALE;
        }

        int changed = attendanceRepository.markAttended(
                studentId, purpose.name(), operatingDay, verifiedAt, method.name());
        if (changed == 1) {
            return AttendanceRecordResult.RECORDED;
        }
        boolean attended = attendanceRepository.findByStudentIdAndPurposeAndOperatingDay(studentId, purpose, operatingDay)
                .map(Attendance::isAttended)
                .orElse(false);
        return attended ? AttendanceRecordResult.ALREADY_ATTENDED : AttendanceRecordResult.SUPERSEDED_BY_MANUAL;
    }
}
