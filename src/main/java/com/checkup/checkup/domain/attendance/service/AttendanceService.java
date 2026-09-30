package com.checkup.checkup.domain.attendance.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private static final Duration MAX_CLOCK_SKEW = Duration.ofSeconds(5);

    private final AttendanceRepository attendanceRepository;
    private final OperatingDayCalculator operatingDayCalculator;
    private final Clock clock;

    /**
     * 자동 인증 성공을 출석으로 기록한다. 동시에 여러 요청이 와도 한 번만 기록된다.
     *
     * 운영일은 호출하는 쪽이 넘기지 않고 인증 시각으로 계산한다. 그 운영일이 오늘이 아니면
     * 늦게 도착한 지난 인증이라 기록하지 않는다(REQ-ATT-007). 인증 시각이 서버 현재 시각보다
     * 5초 넘게 늦으면 기기 시계 오차로 보고 기록하지 않고, 5초 이내로 늦으면 서버 현재 시각으로 낮춰 기록한다
     * (REQ-ATT-002 시계 보정). 미래 시각이 {@code first_verified_at}에 남거나 수동 수정을 덮어쓰지 않게 하기 위해서다.
     *
     * 기록하지 못했을 때 {@code ALREADY_ATTENDED}와 {@code SUPERSEDED_BY_MANUAL}을 가르는 조회는
     * 저장 쿼리와 같은 트랜잭션에서 실행된다. {@code ON CONFLICT DO UPDATE}는 조건이 거짓이라 수정하지 않은
     * 행도 트랜잭션이 끝날 때까지 잠그므로, 그 사이에 다른 트랜잭션이 행을 바꿀 수 없다.
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
        Instant now = clock.instant();
        if (verifiedAt.isAfter(now.plus(MAX_CLOCK_SKEW))) {
            return AttendanceRecordResult.FUTURE;
        }
        Instant recordedAt = verifiedAt.isAfter(now) ? now : verifiedAt;
        LocalDate operatingDay = operatingDayCalculator.of(recordedAt);
        if (!operatingDay.equals(operatingDayCalculator.today())) {
            return AttendanceRecordResult.STALE;
        }

        int changed = attendanceRepository.markAttended(
                studentId, purpose.name(), operatingDay, recordedAt, method.name());
        if (changed == 1) {
            return AttendanceRecordResult.RECORDED;
        }
        boolean attended = attendanceRepository.findAttendedStatus(studentId, purpose, operatingDay).orElse(false);
        return attended ? AttendanceRecordResult.ALREADY_ATTENDED : AttendanceRecordResult.SUPERSEDED_BY_MANUAL;
    }

    /** 출석 완료 알림 문구. */
    private static String attendanceMessage(AttendancePurpose purpose) {
        return switch (purpose) {
            case DORMITORY -> "기숙사 출석이 완료됐어요";
            case STUDY_ROOM -> "자습실 출석이 완료됐어요";
        };
    }
}
