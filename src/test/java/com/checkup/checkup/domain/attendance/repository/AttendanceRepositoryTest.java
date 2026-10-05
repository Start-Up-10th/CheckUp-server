package com.checkup.checkup.domain.attendance.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
 * 층 현황 집계가 그 층 호실의 배정 인원과 요청한 용도·운영일의 출석 인원만 세는지,
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

    @Test
    @DisplayName("층의 호실마다 배정 인원과 요청한 용도·운영일의 출석 인원을 호실 번호순으로 센다")
    void countsAssignedAndAttendedByRoom() {
        Long attended = studentInRoom(1101, 9702);
        Long manuallyAbsent = studentInRoom(1102, 9702);
        Long studyRoomOnly = studentInRoom(1103, 9702);
        Long yesterdayOnly = studentInRoom(1104, 9701);
        studentInRoom(1105, 9701);
        Long lowerFloor = studentInRoom(1201, 9699);
        Long upperFloor = studentInRoom(1202, 9800);
        Long unassigned = student(1203);
        attendance(attended, AttendancePurpose.DORMITORY, DAY, true);
        attendance(manuallyAbsent, AttendancePurpose.DORMITORY, DAY, false);
        attendance(studyRoomOnly, AttendancePurpose.STUDY_ROOM, DAY, true);
        attendance(yesterdayOnly, AttendancePurpose.DORMITORY, DAY.minusDays(1), true);
        attendance(lowerFloor, AttendancePurpose.DORMITORY, DAY, true);
        attendance(upperFloor, AttendancePurpose.DORMITORY, DAY, true);
        attendance(unassigned, AttendancePurpose.DORMITORY, DAY, true);

        List<RoomAttendanceCount> result = attendanceRepository.countByRoom(
                9700, 9799, AttendancePurpose.DORMITORY, DAY);

        assertThat(result)
                .extracting(RoomAttendanceCount::getDormitoryRoom, RoomAttendanceCount::getAssigned,
                        RoomAttendanceCount::getAttended)
                .containsExactly(tuple(9701, 2L, 0L), tuple(9702, 3L, 1L));
    }

    @Test
    @DisplayName("층 현황도 자습실 용도로 물으면 자습실 출석만 센다")
    void countByRoomSeparatesPurpose() {
        Long dormitoryOnly = studentInRoom(1101, 9701);
        Long both = studentInRoom(1102, 9701);
        attendance(dormitoryOnly, AttendancePurpose.DORMITORY, DAY, true);
        attendance(both, AttendancePurpose.DORMITORY, DAY, true);
        attendance(both, AttendancePurpose.STUDY_ROOM, DAY, true);

        List<RoomAttendanceCount> result = attendanceRepository.countByRoom(
                9700, 9799, AttendancePurpose.STUDY_ROOM, DAY);

        assertThat(result)
                .extracting(RoomAttendanceCount::getDormitoryRoom, RoomAttendanceCount::getAssigned,
                        RoomAttendanceCount::getAttended)
                .containsExactly(tuple(9701, 2L, 1L));
    }

    private Long studentInRoom(int studentNumber, int dormitoryRoom) {
        Long studentId = student(studentNumber);
        jdbcTemplate.update("UPDATE student SET dormitory_room = ? WHERE id = ?", dormitoryRoom, studentId);
        return studentId;
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
