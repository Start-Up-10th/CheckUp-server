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
import org.junit.jupiter.api.DisplayName;
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

/**
 * 실제 PostgreSQL에서 자동 인증 출석이 학생·용도·운영일마다 한 번만 기록되고, 수동 수정·늦은 인증·시계 오차 규칙(REQ-ATT-002·007)과 출석 완료 알림 생성을 지키는지 검증한다.
 */
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
    @DisplayName("처음 인증하면 출석으로 기록한다")
    void firstVerificationRecordsAttendance() {
        AttendanceRecordResult result = mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.QR);

        assertThat(result).isEqualTo(AttendanceRecordResult.RECORDED);
        assertThat(attended(AttendancePurpose.DORMITORY, DAY)).isTrue();
        assertThat(firstVerifiedAt(AttendancePurpose.DORMITORY, DAY)).isEqualTo(AT);
        assertThat(method(AttendancePurpose.DORMITORY, DAY)).isEqualTo("QR");
    }

    @Test
    @DisplayName("이미 출석이면 다시 기록하지 않고 최초 시각을 유지한다")
    void alreadyAttendedKeepsFirstVerifiedAt() {
        mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.QR);
        clock.setInstant(AT.plusSeconds(60));

        AttendanceRecordResult result = mark(AttendancePurpose.DORMITORY, AT.plusSeconds(60), AttendanceMethod.FACE);

        assertThat(result).isEqualTo(AttendanceRecordResult.ALREADY_ATTENDED);
        assertThat(firstVerifiedAt(AttendancePurpose.DORMITORY, DAY)).isEqualTo(AT);
        assertThat(method(AttendancePurpose.DORMITORY, DAY)).isEqualTo("QR");
    }

    @Test
    @DisplayName("용도가 다르면 따로 기록한다")
    void differentPurposesAreRecordedSeparately() {
        AttendanceRecordResult dormitory = mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.QR);
        AttendanceRecordResult studyRoom = mark(AttendancePurpose.STUDY_ROOM, AT, AttendanceMethod.QR);

        assertThat(dormitory).isEqualTo(AttendanceRecordResult.RECORDED);
        assertThat(studyRoom).isEqualTo(AttendanceRecordResult.RECORDED);
        assertThat(rowCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("운영일이 바뀌면 새 운영일에 따로 기록한다")
    void newOperatingDayIsRecordedSeparately() {
        mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.QR);
        Instant nextDay = AT.plusSeconds(86_400);
        clock.setInstant(nextDay);

        AttendanceRecordResult result = mark(AttendancePurpose.DORMITORY, nextDay, AttendanceMethod.QR);

        assertThat(result).isEqualTo(AttendanceRecordResult.RECORDED);
        assertThat(attended(AttendancePurpose.DORMITORY, DAY.plusDays(1))).isTrue();
        assertThat(rowCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("동시에 여러 번 들어와도 한 번만 기록한다")
    void concurrentRequestsRecordOnce() throws Exception {
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
    @DisplayName("수동 미출석 뒤에 발생한 인증은 다시 출석으로 바꾸고 최초 시각은 유지한다")
    void verificationAfterManualAbsentMarksAttendedAgain() {
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
    @DisplayName("수동 미출석 전에 발생해 늦게 도착한 인증은 무시한다")
    void lateVerificationBeforeManualAbsentIsIgnored() {
        insertManualAbsent(AT.minusSeconds(3600), AT);

        AttendanceRecordResult result = mark(AttendancePurpose.DORMITORY, AT.minusSeconds(60), AttendanceMethod.FACE);

        assertThat(result).isEqualTo(AttendanceRecordResult.SUPERSEDED_BY_MANUAL);
        assertThat(attended(AttendancePurpose.DORMITORY, DAY)).isFalse();
    }

    @Test
    @DisplayName("어제 운영일 인증이 오늘 도착하면 기록하지 않는다")
    void yesterdayVerificationArrivingTodayIsNotRecorded() {
        clock.setInstant(AT.plusSeconds(86_400));

        AttendanceRecordResult result = mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.FACE);

        assertThat(result).isEqualTo(AttendanceRecordResult.STALE);
        assertThat(rowCount()).isZero();
    }

    @Test
    @DisplayName("오전 8시 전 인증이 8시 뒤에 도착하면 기록하지 않는다")
    void verificationBefore8ArrivingAfter8IsNotRecorded() {
        Instant before8 = Instant.parse("2026-09-27T22:59:00Z");
        clock.setInstant(Instant.parse("2026-09-27T23:01:00Z"));

        AttendanceRecordResult result = mark(AttendancePurpose.DORMITORY, before8, AttendanceMethod.FACE);

        assertThat(result).isEqualTo(AttendanceRecordResult.STALE);
        assertThat(rowCount()).isZero();
    }

    @Test
    @DisplayName("오전 8시 전 인증은 전날 운영일로 기록한다")
    void verificationBefore8IsRecordedOnPreviousDay() {
        Instant before8 = Instant.parse("2026-09-27T22:59:00Z");
        clock.setInstant(before8);

        AttendanceRecordResult result = mark(AttendancePurpose.DORMITORY, before8, AttendanceMethod.QR);

        assertThat(result).isEqualTo(AttendanceRecordResult.RECORDED);
        assertThat(attended(AttendancePurpose.DORMITORY, DAY)).isTrue();
    }

    @Test
    @DisplayName("서버 시각보다 5초 넘게 늦은 인증은 기록하지 않는다")
    void verificationMoreThan5SecondsAheadIsNotRecorded() {
        AttendanceRecordResult result = mark(AttendancePurpose.DORMITORY, AT.plusSeconds(6), AttendanceMethod.FACE);

        assertThat(result).isEqualTo(AttendanceRecordResult.FUTURE);
        assertThat(rowCount()).isZero();
    }

    @Test
    @DisplayName("서버 시각보다 5초 이내로 늦은 인증은 현재 시각으로 기록한다")
    void verificationWithin5SecondsAheadIsRecordedAtNow() {
        AttendanceRecordResult result = mark(AttendancePurpose.DORMITORY, AT.plusSeconds(5), AttendanceMethod.FACE);

        assertThat(result).isEqualTo(AttendanceRecordResult.RECORDED);
        assertThat(firstVerifiedAt(AttendancePurpose.DORMITORY, DAY)).isEqualTo(AT);
    }

    @Test
    @DisplayName("조금 미래인 인증도 현재 시각 이전의 수동 미출석은 덮어쓰지 않는다")
    void slightlyFutureVerificationDoesNotOverrideEarlierManualAbsent() {
        insertManualAbsent(AT.minusSeconds(3600), AT);

        AttendanceRecordResult result = mark(AttendancePurpose.DORMITORY, AT.plusSeconds(3), AttendanceMethod.FACE);

        assertThat(result).isEqualTo(AttendanceRecordResult.SUPERSEDED_BY_MANUAL);
        assertThat(attended(AttendancePurpose.DORMITORY, DAY)).isFalse();
    }

    @Test
    @DisplayName("학생을 삭제하면 출석도 함께 삭제된다")
    void deletingStudentDeletesAttendance() {
        mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.QR);

        jdbcTemplate.update("DELETE FROM student WHERE id = ?", studentId);

        assertThat(rowCount()).isZero();
    }

    @Test
    @DisplayName("새로 출석하면 용도와 운영일로 출석 완료 알림을 하나 만든다")
    void newAttendanceCreatesOneNotification() {
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
    @DisplayName("이미 출석한 뒤 다시 인증해도 출석 알림을 늘리지 않는다")
    void repeatedVerificationDoesNotAddNotification() {
        mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.QR);
        clock.setInstant(AT.plusSeconds(60));

        mark(AttendancePurpose.DORMITORY, AT.plusSeconds(60), AttendanceMethod.FACE);

        assertThat(notificationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("동시에 여러 번 들어와도 출석 알림은 하나다")
    void concurrentRequestsCreateOneNotification() throws Exception {
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
    @DisplayName("기록하지 않은 늦은 인증은 출석 알림을 만들지 않는다")
    void staleVerificationCreatesNoNotification() {
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
