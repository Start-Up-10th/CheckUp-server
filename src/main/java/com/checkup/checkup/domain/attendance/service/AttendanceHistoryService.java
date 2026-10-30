package com.checkup.checkup.domain.attendance.service;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.attendance.repository.FloorStudentAttendance;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.time.OperatingDayCalculator;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 출입 시각 없는 출석 요약을 지난 30개 운영일 동안 보관한다(REQ-ATT-007, DEC-035). */
@Service
@RequiredArgsConstructor
public class AttendanceHistoryService {
    public static final int RETENTION_DAYS = 30;
    private final JdbcTemplate jdbc;
    private final OperatingDayCalculator operatingDayCalculator;

    public LocalDate resolveDate(LocalDate date) {
        LocalDate today = operatingDayCalculator.today();
        LocalDate selected = date == null ? today : date;
        if (selected.isAfter(today) || selected.isBefore(today.minusDays(RETENTION_DAYS))) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return selected;
    }

    public void verifyWritableDate(LocalDate date) {
        if (!resolveDate(date).equals(operatingDayCalculator.today())) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
    }

    /** 현재 학생 이름·학번과 당시 호실·출석 여부만 읽는다. 삭제된 학생은 FK cascade로 함께 제거된다. */
    @Transactional(readOnly = true)
    public List<FloorStudentAttendance> findStudents(int firstRoom, int lastRoom,
                                                    AttendancePurpose purpose, LocalDate day) {
        return jdbc.query("""
                SELECT s.id, h.dormitory_room, s.datagsm_student_id, s.name,
                       s.class_number, s.student_number, h.attended
                FROM attendance_summary h JOIN student s ON s.id = h.student_id
                WHERE h.dormitory_room BETWEEN ? AND ? AND h.purpose = ? AND h.operating_day = ?
                  AND s.datagsm_student_id IS NOT NULL
                """, (rs, row) -> new FloorStudentAttendance(rs.getLong("id"), rs.getInt("dormitory_room"),
                rs.getLong("datagsm_student_id"), rs.getString("name"), rs.getInt("class_number"),
                rs.getInt("student_number"), rs.getBoolean("attended")), firstRoom, lastRoom, purpose.name(), day);
    }

    /** 출석 변경과 같은 트랜잭션에서 요약을 갱신한다. 기존 운영일의 호실 배정은 유지한다. */
    @Transactional
    public void capture(Long studentId, AttendancePurpose purpose, LocalDate day) {
        jdbc.update("""
                INSERT INTO attendance_summary (student_id, operating_day, purpose, dormitory_room, attended)
                SELECT s.id, a.operating_day, a.purpose, s.dormitory_room, a.attended
                FROM student s JOIN attendance a ON a.student_id = s.id
                WHERE s.id = ? AND a.purpose = ? AND a.operating_day = ?
                  AND s.dormitory_room IS NOT NULL AND s.datagsm_student_id IS NOT NULL
                ON CONFLICT (student_id, purpose, operating_day) DO UPDATE SET attended = EXCLUDED.attended
                """, studentId, purpose.name(), day);
    }

    /** 미출석 학생도 명단에 남긴다. 이미 만든 요약의 출석 상태·당시 호실은 바꾸지 않는다. */
    private void captureRoster(LocalDate day) {
        jdbc.update("""
                INSERT INTO attendance_summary (student_id, operating_day, purpose, dormitory_room, attended)
                SELECT s.id, CAST(? AS DATE), p.purpose, s.dormitory_room, COALESCE(a.attended, FALSE)
                FROM student s CROSS JOIN (VALUES ('DORMITORY'), ('STUDY_ROOM')) AS p(purpose)
                LEFT JOIN attendance a ON a.student_id = s.id AND a.purpose = p.purpose AND a.operating_day = ?
                WHERE s.dormitory_room IS NOT NULL AND s.datagsm_student_id IS NOT NULL
                ON CONFLICT (student_id, purpose, operating_day) DO NOTHING
                """, day, day);
    }

    /** 시작·08시 정리에서 호출한다. 원본 폐기 전에 남은 원본을 요약하고 오늘 명단을 준비한다. */
    @Transactional
    public void archiveAndPrune(LocalDate today) {
        LocalDate cutoff = today.minusDays(RETENTION_DAYS);
        // 출석 쓰기도 원본 → 요약 순으로 잠근다. 경계 직전 쓰기와 정리가 서로 역순으로 기다리지 않게 한다.
        jdbc.queryForList("""
                SELECT id FROM attendance WHERE operating_day < ?
                ORDER BY student_id, purpose, operating_day FOR UPDATE
                """, Long.class, today);
        // 도입 전 이미 폐기된 기록과 서버가 꺼져 있던 날의 미출석 명단은 만들어 내지 않는다.
        List<LocalDate> days = jdbc.queryForList("""
                SELECT operating_day FROM attendance_summary WHERE operating_day = ?
                UNION SELECT operating_day FROM attendance WHERE operating_day >= ? AND operating_day < ?
                """, LocalDate.class, today.minusDays(1), cutoff, today);
        days.forEach(this::captureRoster);
        jdbc.update("""
                INSERT INTO attendance_summary (student_id, operating_day, purpose, dormitory_room, attended)
                SELECT a.student_id, a.operating_day, a.purpose, s.dormitory_room, a.attended
                FROM attendance a JOIN student s ON s.id = a.student_id
                WHERE a.operating_day >= ? AND a.operating_day < ?
                  AND s.dormitory_room IS NOT NULL AND s.datagsm_student_id IS NOT NULL
                ON CONFLICT (student_id, purpose, operating_day) DO UPDATE SET attended = EXCLUDED.attended
                """, cutoff, today);
        captureRoster(today);
        jdbc.update("DELETE FROM attendance_summary WHERE operating_day < ?", cutoff);
    }
}
