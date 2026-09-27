package com.checkup.checkup.domain.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.checkup.checkup.domain.attendance.entity.AttendanceMethod;
import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(AttendanceService.class)
class AttendanceServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 27);
    private static final Instant AT = Instant.parse("2026-09-27T12:00:00Z");

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long memberId;
    private Long studentId;

    @BeforeEach
    void setUp() {
        long datagsmId = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
        memberId = jdbcTemplate.queryForObject(
                "INSERT INTO member (datagsm_id, name, role) VALUES (?, '테스트학생', 'STUDENT') RETURNING id",
                Long.class, datagsmId);
        studentId = jdbcTemplate.queryForObject(
                "INSERT INTO student (member_id, number, grade, class_number, student_number) VALUES (?, 1, 1, 1, 1101) RETURNING id",
                Long.class, memberId);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM attendance WHERE student_id = ?", studentId);
        jdbcTemplate.update("DELETE FROM student WHERE id = ?", studentId);
        jdbcTemplate.update("DELETE FROM member WHERE id = ?", memberId);
    }

    @Test
    void 처음_인증하면_출석으로_기록한다() {
        boolean recorded = attendanceService.markAttended(studentId, AttendancePurpose.DORMITORY, DAY, AT, AttendanceMethod.QR);

        assertThat(recorded).isTrue();
        assertThat(attended(AttendancePurpose.DORMITORY, DAY)).isTrue();
        assertThat(firstVerifiedAt(AttendancePurpose.DORMITORY, DAY)).isEqualTo(AT);
        assertThat(method(AttendancePurpose.DORMITORY, DAY)).isEqualTo("QR");
    }

    @Test
    void 이미_출석이면_다시_기록하지_않고_최초_시각을_유지한다() {
        attendanceService.markAttended(studentId, AttendancePurpose.DORMITORY, DAY, AT, AttendanceMethod.QR);

        boolean recorded = attendanceService.markAttended(
                studentId, AttendancePurpose.DORMITORY, DAY, AT.plusSeconds(60), AttendanceMethod.FACE);

        assertThat(recorded).isFalse();
        assertThat(firstVerifiedAt(AttendancePurpose.DORMITORY, DAY)).isEqualTo(AT);
        assertThat(method(AttendancePurpose.DORMITORY, DAY)).isEqualTo("QR");
    }

    @Test
    void 용도가_다르면_따로_기록한다() {
        boolean dormitory = attendanceService.markAttended(studentId, AttendancePurpose.DORMITORY, DAY, AT, AttendanceMethod.QR);
        boolean studyRoom = attendanceService.markAttended(studentId, AttendancePurpose.STUDY_ROOM, DAY, AT, AttendanceMethod.QR);

        assertThat(dormitory).isTrue();
        assertThat(studyRoom).isTrue();
        assertThat(rowCount()).isEqualTo(2);
    }

    @Test
    void 운영일이_다르면_따로_기록한다() {
        boolean today = attendanceService.markAttended(studentId, AttendancePurpose.DORMITORY, DAY, AT, AttendanceMethod.QR);
        boolean nextDay = attendanceService.markAttended(
                studentId, AttendancePurpose.DORMITORY, DAY.plusDays(1), AT.plusSeconds(86_400), AttendanceMethod.QR);

        assertThat(today).isTrue();
        assertThat(nextDay).isTrue();
        assertThat(rowCount()).isEqualTo(2);
    }

    @Test
    void 동시에_여러_번_들어와도_한_번만_기록한다() throws Exception {
        int requests = 10;
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            Callable<Boolean> task = () -> {
                start.await();
                return attendanceService.markAttended(studentId, AttendancePurpose.DORMITORY, DAY, AT, AttendanceMethod.QR);
            };
            results.add(executor.submit(task));
        }
        start.countDown();

        int recorded = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                recorded++;
            }
        }
        executor.shutdown();

        assertThat(recorded).isEqualTo(1);
        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    void 수동_미출석_뒤에_발생한_인증은_다시_출석으로_바꾸고_최초_시각은_유지한다() {
        Instant firstAt = AT.minusSeconds(3600);
        Instant manualAt = AT.minusSeconds(600);
        insertManualAbsent(firstAt, manualAt);

        boolean recorded = attendanceService.markAttended(studentId, AttendancePurpose.DORMITORY, DAY, AT, AttendanceMethod.QR);

        assertThat(recorded).isTrue();
        assertThat(attended(AttendancePurpose.DORMITORY, DAY)).isTrue();
        assertThat(firstVerifiedAt(AttendancePurpose.DORMITORY, DAY)).isEqualTo(firstAt);
        assertThat(method(AttendancePurpose.DORMITORY, DAY)).isEqualTo("FACE");
    }

    @Test
    void 수동_미출석_전에_발생해_늦게_도착한_인증은_무시한다() {
        Instant manualAt = AT;
        insertManualAbsent(AT.minusSeconds(3600), manualAt);

        boolean recorded = attendanceService.markAttended(
                studentId, AttendancePurpose.DORMITORY, DAY, manualAt.minusSeconds(60), AttendanceMethod.QR);

        assertThat(recorded).isFalse();
        assertThat(attended(AttendancePurpose.DORMITORY, DAY)).isFalse();
    }

    private void insertManualAbsent(Instant firstVerifiedAt, Instant manualUpdatedAt) {
        jdbcTemplate.update(
                "INSERT INTO attendance (student_id, purpose, operating_day, attended, first_verified_at, method, manual_updated_at) "
                        + "VALUES (?, 'DORMITORY', ?, FALSE, ?, 'FACE', ?)",
                studentId, DAY, Timestamp.from(firstVerifiedAt), Timestamp.from(manualUpdatedAt));
    }

    private boolean attended(AttendancePurpose purpose, LocalDate day) {
        return jdbcTemplate.queryForObject(
                "SELECT attended FROM attendance WHERE student_id = ? AND purpose = ? AND operating_day = ?",
                Boolean.class, studentId, purpose.name(), day);
    }

    private Instant firstVerifiedAt(AttendancePurpose purpose, LocalDate day) {
        return jdbcTemplate.queryForObject(
                "SELECT first_verified_at FROM attendance WHERE student_id = ? AND purpose = ? AND operating_day = ?",
                Timestamp.class, studentId, purpose.name(), day).toInstant();
    }

    private String method(AttendancePurpose purpose, LocalDate day) {
        return jdbcTemplate.queryForObject(
                "SELECT method FROM attendance WHERE student_id = ? AND purpose = ? AND operating_day = ?",
                String.class, studentId, purpose.name(), day);
    }

    private int rowCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM attendance WHERE student_id = ?", Integer.class, studentId);
    }
}
