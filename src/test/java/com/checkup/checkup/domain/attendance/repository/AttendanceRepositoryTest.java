package com.checkup.checkup.domain.attendance.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;

/**
 * 실제 PostgreSQL에서 호실 명단용 출석 조회가 요청한 학생·용도·운영일의 출석 상태만 돌려주는지,
 * 지난 운영일 출석 삭제가 기준 운영일 전의 행만 지우는지 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AttendanceRepositoryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 4);
    /** 삭제 테스트 기준일. 같은 DB의 다른 출석 행을 지우지 않도록 실제 운영일보다 훨씬 이른 날을 쓴다. */
    private static final LocalDate PURGE_DAY = LocalDate.of(2000, 1, 2);

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Long> memberIds = new ArrayList<>();
    private final List<Long> studentIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long studentId : studentIds) {
            jdbcTemplate.update("DELETE FROM attendance WHERE student_id = ?", studentId);
            jdbcTemplate.update("DELETE FROM student WHERE id = ?", studentId);
        }
        for (Long memberId : memberIds) {
            jdbcTemplate.update("DELETE FROM member WHERE id = ?", memberId);
        }
    }

    @Test
    @DisplayName("요청한 용도·운영일에 지금 출석 상태인 학생만 돌려준다")
    void returnsOnlyStudentsAttendedForPurposeAndDay() {
        Long attended = student(1101);
        Long manuallyAbsent = student(1102);
        Long studyRoomOnly = student(1103);
        Long yesterdayOnly = student(1104);
        Long noRecord = student(1105);
        Long otherRoom = student(1201);
        attendance(attended, AttendancePurpose.DORMITORY, DAY, true);
        attendance(manuallyAbsent, AttendancePurpose.DORMITORY, DAY, false);
        attendance(studyRoomOnly, AttendancePurpose.STUDY_ROOM, DAY, true);
        attendance(yesterdayOnly, AttendancePurpose.DORMITORY, DAY.minusDays(1), true);
        attendance(otherRoom, AttendancePurpose.DORMITORY, DAY, true);

        List<Long> result = attendanceRepository.findAttendedStudentIds(
                List.of(attended, manuallyAbsent, studyRoomOnly, yesterdayOnly, noRecord),
                AttendancePurpose.DORMITORY, DAY);

        assertThat(result).containsExactly(attended);
    }

    @Test
    @DisplayName("자습실 용도로 물으면 자습실 출석만 본다")
    void studyRoomPurposeIsSeparate() {
        Long dormitoryOnly = student(1101);
        Long studyRoom = student(1102);
        attendance(dormitoryOnly, AttendancePurpose.DORMITORY, DAY, true);
        attendance(studyRoom, AttendancePurpose.STUDY_ROOM, DAY, true);

        assertThat(attendanceRepository.findAttendedStudentIds(
                List.of(dormitoryOnly, studyRoom), AttendancePurpose.STUDY_ROOM, DAY))
                .containsExactly(studyRoom);
    }

    @Test
    @DisplayName("기준 운영일 전의 출석은 용도와 출석 여부에 상관없이 지우고 기준 운영일 출석은 남긴다")
    void deleteByOperatingDayBeforeDeletesOnlyPastDays() {
        Long student = student(1101);
        attendance(student, AttendancePurpose.DORMITORY, PURGE_DAY.minusDays(1), true);
        attendance(student, AttendancePurpose.STUDY_ROOM, PURGE_DAY.minusDays(1), false);
        attendance(student, AttendancePurpose.DORMITORY, PURGE_DAY, true);

        Integer deleted = new TransactionTemplate(transactionManager)
                .execute(status -> attendanceRepository.deleteByOperatingDayBefore(PURGE_DAY));

        assertThat(deleted).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList(
                "SELECT operating_day FROM attendance WHERE student_id = ?", LocalDate.class, student))
                .containsExactly(PURGE_DAY);
    }

    private Long student(int studentNumber) {
        long datagsmId = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
        Long memberId = jdbcTemplate.queryForObject(
                "INSERT INTO member (datagsm_id, name, role) VALUES (?, '테스트학생', 'STUDENT') RETURNING id",
                Long.class, datagsmId);
        Long studentId = jdbcTemplate.queryForObject(
                "INSERT INTO student (member_id, name, number, grade, class_number, student_number) VALUES (?, '테스트학생', 1, 1, 1, ?) RETURNING id",
                Long.class, memberId, studentNumber);
        memberIds.add(memberId);
        studentIds.add(studentId);
        return studentId;
    }

    private void attendance(Long studentId, AttendancePurpose purpose, LocalDate day, boolean attended) {
        jdbcTemplate.update(
                "INSERT INTO attendance (student_id, purpose, operating_day, attended) VALUES (?, ?, ?, ?)",
                studentId, purpose.name(), day, attended);
    }
}
