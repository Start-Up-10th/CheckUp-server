package com.checkup.checkup.domain.attendance.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.checkup.checkup.domain.attendance.dto.response.MyAttendanceResponse;
import com.checkup.checkup.domain.attendance.entity.AttendanceMethod;
import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.attendance.entity.AttendanceRecordResult;
import com.checkup.checkup.domain.attendance.repository.AttendanceRepository;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.notification.entity.NotificationType;
import com.checkup.checkup.domain.notification.service.NotificationService;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.time.OperatingDayCalculator;

import lombok.RequiredArgsConstructor;

/**
 * 출석 기록을 담당한다. QR·얼굴 인식은 인증 성공 후 이 서비스로 출석을 확정하고, 관리자 수동 수정도 이 서비스로 저장한다.
 * 학생 본인의 오늘 출석 조회도 여기서 한다.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private static final Duration MAX_CLOCK_SKEW = Duration.ofSeconds(5);

    private final AttendanceRepository attendanceRepository;
    private final OperatingDayCalculator operatingDayCalculator;
    private final Clock clock;
    private final NotificationService notificationService;
    private final StudentRepository studentRepository;

    /**
     * 자동 인증 성공을 출석으로 기록한다. 동시에 여러 요청이 와도 한 번만 기록된다.
     * 새로 기록했을 때만 같은 트랜잭션에서 출석 완료 알림을 만든다. 중복·늦은 인증은 알림을 만들지 않는다(REQ-ATT-002).
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
            notificationService.create(
                    studentId,
                    NotificationType.ATTENDANCE,
                    purpose.name() + ":" + operatingDay,
                    attendanceMessage(purpose));
            return AttendanceRecordResult.RECORDED;
        }
        boolean attended = attendanceRepository.findAttendedStatus(studentId, purpose, operatingDay).orElse(false);
        return attended ? AttendanceRecordResult.ALREADY_ATTENDED : AttendanceRecordResult.SUPERSEDED_BY_MANUAL;
    }

    /**
     * 관리자가 고른 학생별 출석 상태를 오늘 운영일에 저장한다(REQ-ATT-006).
     *
     * 상태가 실제로 바뀌는 학생만 수정하고 수정 시각을 남긴다. 이미 같은 상태인 학생은 건드리지 않아,
     * 관리자가 바꾸지 않은 학생의 늦은 인증까지 막지 않는다(DEC-008).
     * 수동으로 새로 출석이 된 학생에게는 자동 인증과 같이 출석 완료 알림을 만든다. 같은 용도·운영일의 알림은 하나다.
     * 여러 관리자가 동시에 저장해도 서로 기다리다 멈추지 않도록 학생 id 순서로 처리한다.
     *
     * @param attendedByStudentId 학생 id별 출석 여부. 출석이면 true
     * @param purpose             출석 용도
     */
    @Transactional
    public void saveManually(Map<Long, Boolean> attendedByStudentId, AttendancePurpose purpose) {
        Instant now = clock.instant();
        LocalDate operatingDay = operatingDayCalculator.of(now);
        new TreeMap<>(attendedByStudentId).forEach((studentId, attended) -> {
            if (!attended) {
                attendanceRepository.markManuallyAbsent(studentId, purpose.name(), operatingDay, now);
                return;
            }
            int changed = attendanceRepository.markManuallyAttended(studentId, purpose.name(), operatingDay, now);
            if (changed == 1) {
                notificationService.create(
                        studentId,
                        NotificationType.ATTENDANCE,
                        purpose.name() + ":" + operatingDay,
                        attendanceMessage(purpose));
            }
        });
    }

    /**
     * 로그인한 학생 본인의 오늘 운영일 출석 상태를 용도별로 조회한다. 기록이 없는 용도는 미출석이다.
     * 학생은 요청 값이 아니라 세션의 회원으로 정해, 다른 학생의 출석을 볼 수 없다.
     *
     * @param memberId 세션의 회원 id
     * @return 오늘 운영일과 기숙사 입소·자습실 출석 상태
     * @throws CustomException 학생이 아니면 {@link ErrorCode#MISSING_STUDENT_INFO}(403)
     */
    @Transactional(readOnly = true)
    public MyAttendanceResponse getMyToday(Long memberId) {
        Student student = studentRepository.findByMemberId(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MISSING_STUDENT_INFO));
        LocalDate operatingDay = operatingDayCalculator.today();
        return MyAttendanceResponse.of(operatingDay,
                attendanceRepository.findAllByStudentIdAndOperatingDay(student.getId(), operatingDay));
    }

    /** 출석 완료 알림 문구. */
    private static String attendanceMessage(AttendancePurpose purpose) {
        return switch (purpose) {
            case DORMITORY -> "기숙사 출석이 완료됐어요";
            case STUDY_ROOM -> "자습실 출석이 완료됐어요";
        };
    }

    /**
     * 오늘 운영일 전의 출석 기록을 지운다. 수동 출석 상태도 같은 행에 있어 함께 지워진다(REQ-ATT-007).
     * 지운 뒤 늦게 도착한 전날 인증은 {@link #markAttended}가 STALE로 버려 기록을 되살리지 않는다.
     *
     * @return 지운 행 수
     */
    @Transactional
    public int deleteExpired() {
        LocalDate today = operatingDayCalculator.today();
        return attendanceRepository.deleteByOperatingDayBefore(today);
    }
}
