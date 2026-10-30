package com.checkup.checkup.domain.attendance.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

/** 실제 PostgreSQL에서 층 명단의 범위·용도·운영일·수동 상태와 단일 SQL 조회를 검증한다(REQ-UI-001, REQ-ATT-001). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FloorStudentAttendanceRepositoryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 30);

    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("여러 호실과 미로그인 학생을 한 SQL로 조회하고 다른 층·용도·지난 운영일 출석을 제외한다")
    void readsFloorStudentsWithOneSqlAndCurrentAttendance() {
        Long attended = student(9700, 1001L, "학생A");
        Long manuallyAbsent = student(9701, 1002L, "학생B");
        Long otherPurpose = student(9701, 1003L, "학생C");
        Long yesterday = student(9799, 1004L, "학생D");
        Long noRecord = student(9799, 1005L, "학생E");
        student(9699, 1006L, "아래층");
        student(9800, 1007L, "위층");
        student(null, 1008L, "미배정");
        student(9701, null, "외부id없음");
        attendance(attended, AttendancePurpose.DORMITORY, DAY, true);
        attendance(attended, AttendancePurpose.STUDY_ROOM, DAY, true);
        attendance(attended, AttendancePurpose.DORMITORY, DAY.minusDays(1), true);
        attendance(manuallyAbsent, AttendancePurpose.DORMITORY, DAY, false);
        attendance(otherPurpose, AttendancePurpose.STUDY_ROOM, DAY, true);
        attendance(yesterday, AttendancePurpose.DORMITORY, DAY.minusDays(1), true);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean statisticsEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            List<FloorStudentAttendance> result = attendanceRepository.findFloorStudents(
                    9700, 9799, AttendancePurpose.DORMITORY, DAY);

            assertThat(result).extracting(FloorStudentAttendance::studentId, FloorStudentAttendance::dormitoryRoom,
                            FloorStudentAttendance::datagsmStudentId, FloorStudentAttendance::studentName,
                            FloorStudentAttendance::studentClass, FloorStudentAttendance::studentNumber,
                            FloorStudentAttendance::attended)
                    .containsExactlyInAnyOrder(
                            tuple(attended, 9700, 1001L, "학생A", 2, 1201, true),
                            tuple(manuallyAbsent, 9701, 1002L, "학생B", 2, 1201, false),
                            tuple(otherPurpose, 9701, 1003L, "학생C", 2, 1201, false),
                            tuple(yesterday, 9799, 1004L, "학생D", 2, 1201, false),
                            tuple(noRecord, 9799, 1005L, "학생E", 2, 1201, false));
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
            assertThat(statistics.getEntityLoadCount()).isZero();
        } finally {
            statistics.setStatisticsEnabled(statisticsEnabled);
        }
    }

    @Test
    @DisplayName("자습실 조회는 자습실의 오늘 출석만 반영한다")
    void separatesStudyRoomAttendance() {
        Long dormitory = student(9701, 1001L, "학생A");
        Long studyRoom = student(9702, 1002L, "학생B");
        attendance(dormitory, AttendancePurpose.DORMITORY, DAY, true);
        attendance(studyRoom, AttendancePurpose.STUDY_ROOM, DAY, true);

        assertThat(attendanceRepository.findFloorStudents(9700, 9799, AttendancePurpose.STUDY_ROOM, DAY))
                .extracting(FloorStudentAttendance::studentId, FloorStudentAttendance::attended)
                .containsExactlyInAnyOrder(tuple(dormitory, false), tuple(studyRoom, true));
    }

    @Test
    @DisplayName("학생이 없는 층은 빈 목록이다")
    void emptyFloorReturnsEmptyList() {
        assertThat(attendanceRepository.findFloorStudents(9700, 9799, AttendancePurpose.DORMITORY, DAY))
                .isEmpty();
    }

    private Long student(Integer room, Long datagsmId, String name) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO student (name, datagsm_student_id, number, grade, class_number, student_number, dormitory_room)
                VALUES (?, ?, 1, 1, 2, 1201, ?) RETURNING id
                """, Long.class, name, datagsmId, room);
    }

    private void attendance(Long studentId, AttendancePurpose purpose, LocalDate day, boolean attended) {
        jdbcTemplate.update("""
                INSERT INTO attendance (student_id, purpose, operating_day, attended) VALUES (?, ?, ?, ?)
                """, studentId, purpose.name(), day, attended);
    }
}
