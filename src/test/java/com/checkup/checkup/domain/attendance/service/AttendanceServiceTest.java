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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.checkup.checkup.domain.attendance.entity.AttendanceMethod;
import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.attendance.entity.AttendanceRecordResult;
import com.checkup.checkup.global.time.OperatingDayCalculator;
import com.checkup.checkup.support.MutableClock;
import com.checkup.checkup.domain.notification.service.NotificationService;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({AttendanceService.class, NotificationService.class, OperatingDayCalculator.class,
        AttendanceServiceTest.ClockTestConfig.class})
class AttendanceServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 27);
    private static final Instant AT = Instant.parse("2026-09-27T12:00:00Z");

    @TestConfiguration
    static class ClockTestConfig {
        @Bean
        MutableClock clock() {
            return new MutableClock(AT);
        }
    }

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    private Long memberId;
    private Long studentId;

    @BeforeEach
    void setUp() {
        clock.setInstant(AT);
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
        AttendanceRecordResult result = mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.QR);

        assertThat(result).isEqualTo(AttendanceRecordResult.RECORDED);
        assertThat(attended(AttendancePurpose.DORMITORY, DAY)).isTrue();
        assertThat(firstVerifiedAt(AttendancePurpose.DORMITORY, DAY)).isEqualTo(AT);
        assertThat(method(AttendancePurpose.DORMITORY, DAY)).isEqualTo("QR");
    }

    @Test
    void 이미_출석이면_다시_기록하지_않고_최초_시각을_유지한다() {
        mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.QR);
        clock.setInstant(AT.plusSeconds(60));

        AttendanceRecordResult result = mark(AttendancePurpose.DORMITORY, AT.plusSeconds(60), AttendanceMethod.FACE);

        assertThat(result).isEqualTo(AttendanceRecordResult.ALREADY_ATTENDED);
        assertThat(firstVerifiedAt(AttendancePurpose.DORMITORY, DAY)).isEqualTo(AT);
        assertThat(method(AttendancePurpose.DORMITORY, DAY)).isEqualTo("QR");
    }

    @Test
    void 용도가_다르면_따로_기록한다() {
        AttendanceRecordResult dormitory = mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.QR);
        AttendanceRecordResult studyRoom = mark(AttendancePurpose.STUDY_ROOM, AT, AttendanceMethod.QR);

        assertThat(dormitory).isEqualTo(AttendanceRecordResult.RECORDED);
        assertThat(studyRoom).isEqualTo(AttendanceRecordResult.RECORDED);
        assertThat(rowCount()).isEqualTo(2);
    }

    @Test
    void 운영일이_바뀌면_새_운영일에_따로_기록한다() {
        mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.QR);
        Instant nextDay = AT.plusSeconds(86_400);
        clock.setInstant(nextDay);

        AttendanceRecordResult result = mark(AttendancePurpose.DORMITORY, nextDay, AttendanceMethod.QR);

        assertThat(result).isEqualTo(AttendanceRecordResult.RECORDED);
        assertThat(attended(AttendancePurpose.DORMITORY, DAY.plusDays(1))).isTrue();
        assertThat(rowCount()).isEqualTo(2);
    }

    @Test
    void 동시에_여러_번_들어와도_한_번만_기록한다() throws Exception {
        int requests = 10;
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AttendanceRecordResult>> results = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            Callable<AttendanceRecordResult> task = () -> {
                start.await();
                return mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.QR);
            };
            results.add(executor.submit(task));
        }
        start.countDown();

        int recorded = 0;
        for (Future<AttendanceRecordResult> result : results) {
            if (result.get() == AttendanceRecordResult.RECORDED) {
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

        AttendanceRecordResult result = mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.QR);

        assertThat(result).isEqualTo(AttendanceRecordResult.RECORDED);
        assertThat(attended(AttendancePurpose.DORMITORY, DAY)).isTrue();
        assertThat(firstVerifiedAt(AttendancePurpose.DORMITORY, DAY)).isEqualTo(firstAt);
        assertThat(method(AttendancePurpose.DORMITORY, DAY)).isEqualTo("FACE");
    }

    @Test
    void 수동_미출석_전에_발생해_늦게_도착한_인증은_무시한다() {
        insertManualAbsent(AT.minusSeconds(3600), AT);

        AttendanceRecordResult result = mark(AttendancePurpose.DORMITORY, AT.minusSeconds(60), AttendanceMethod.FACE);

        assertThat(result).isEqualTo(AttendanceRecordResult.SUPERSEDED_BY_MANUAL);
        assertThat(attended(AttendancePurpose.DORMITORY, DAY)).isFalse();
    }

    @Test
    void 어제_운영일_인증이_오늘_도착하면_기록하지_않는다() {
        clock.setInstant(AT.plusSeconds(86_400));

        AttendanceRecordResult result = mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.FACE);

        assertThat(result).isEqualTo(AttendanceRecordResult.STALE);
        assertThat(rowCount()).isZero();
    }

    @Test
    void 오전_8시_전_인증이_8시_뒤에_도착하면_기록하지_않는다() {
        Instant before8 = Instant.parse("2026-09-27T22:59:00Z");
        clock.setInstant(Instant.parse("2026-09-27T23:01:00Z"));

        AttendanceRecordResult result = mark(AttendancePurpose.DORMITORY, before8, AttendanceMethod.FACE);

        assertThat(result).isEqualTo(AttendanceRecordResult.STALE);
        assertThat(rowCount()).isZero();
    }

    @Test
    void 오전_8시_전_인증은_전날_운영일로_기록한다() {
        Instant before8 = Instant.parse("2026-09-27T22:59:00Z");
        clock.setInstant(before8);

        AttendanceRecordResult result = mark(AttendancePurpose.DORMITORY, before8, AttendanceMethod.QR);

        assertThat(result).isEqualTo(AttendanceRecordResult.RECORDED);
        assertThat(attended(AttendancePurpose.DORMITORY, DAY)).isTrue();
    }

    @Test
    void 서버_시각보다_5초_넘게_늦은_인증은_기록하지_않는다() {
        AttendanceRecordResult result = mark(AttendancePurpose.DORMITORY, AT.plusSeconds(6), AttendanceMethod.FACE);

        assertThat(result).isEqualTo(AttendanceRecordResult.FUTURE);
        assertThat(rowCount()).isZero();
    }

    @Test
    void 서버_시각보다_5초_이내로_늦은_인증은_현재_시각으로_기록한다() {
        AttendanceRecordResult result = mark(AttendancePurpose.DORMITORY, AT.plusSeconds(5), AttendanceMethod.FACE);

        assertThat(result).isEqualTo(AttendanceRecordResult.RECORDED);
        assertThat(firstVerifiedAt(AttendancePurpose.DORMITORY, DAY)).isEqualTo(AT);
    }

    @Test
    void 조금_미래인_인증도_현재_시각_이전의_수동_미출석은_덮어쓰지_않는다() {
        insertManualAbsent(AT.minusSeconds(3600), AT);

        AttendanceRecordResult result = mark(AttendancePurpose.DORMITORY, AT.plusSeconds(3), AttendanceMethod.FACE);

        assertThat(result).isEqualTo(AttendanceRecordResult.SUPERSEDED_BY_MANUAL);
        assertThat(attended(AttendancePurpose.DORMITORY, DAY)).isFalse();
    }

    @Test
    void 학생을_삭제하면_출석도_함께_삭제된다() {
        mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.QR);

        jdbcTemplate.update("DELETE FROM student WHERE id = ?", studentId);

        assertThat(rowCount()).isZero();
    }

    @Test
    void 새로_출석하면_용도와_운영일로_출석_완료_알림을_하나_만든다() {
        mark(AttendancePurpose.STUDY_ROOM, AT, AttendanceMethod.QR);

        assertThat(notificationCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT source_key FROM notification WHERE student_id = ? AND type = 'ATTENDANCE'",
                String.class, studentId)).isEqualTo("STUDY_ROOM:2026-09-27");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT message FROM notification WHERE student_id = ?", String.class, studentId))
                .isEqualTo("자습실 출석이 완료됐어요");
    }

    @Test
    void 이미_출석한_뒤_다시_인증해도_출석_알림을_늘리지_않는다() {
        mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.QR);
        clock.setInstant(AT.plusSeconds(60));

        mark(AttendancePurpose.DORMITORY, AT.plusSeconds(60), AttendanceMethod.FACE);

        assertThat(notificationCount()).isEqualTo(1);
    }

    @Test
    void 동시에_여러_번_들어와도_출석_알림은_하나다() throws Exception {
        int requests = 10;
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AttendanceRecordResult>> results = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            results.add(executor.submit(() -> {
                start.await();
                return mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.QR);
            }));
        }
        start.countDown();
        for (Future<AttendanceRecordResult> result : results) {
            result.get();
        }
        executor.shutdown();

        assertThat(notificationCount()).isEqualTo(1);
    }

    @Test
    void 기록하지_않은_늦은_인증은_출석_알림을_만들지_않는다() {
        clock.setInstant(AT.plusSeconds(86_400));

        mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.FACE);

        assertThat(notificationCount()).isZero();
    }

    private AttendanceRecordResult mark(AttendancePurpose purpose, Instant verifiedAt, AttendanceMethod method) {
        return attendanceService.markAttended(studentId, purpose, verifiedAt, method);
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

    private int notificationCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM notification WHERE student_id = ?", Integer.class, studentId);
    }

    private int rowCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM attendance WHERE student_id = ?", Integer.class, studentId);
    }
}
