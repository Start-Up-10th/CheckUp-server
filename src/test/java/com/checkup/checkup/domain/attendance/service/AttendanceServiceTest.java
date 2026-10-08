package com.checkup.checkup.domain.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

import com.checkup.checkup.domain.attendance.dto.response.MyAttendanceResponse;
import com.checkup.checkup.domain.attendance.entity.AttendanceMethod;
import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.attendance.entity.AttendanceRecordResult;
import com.checkup.checkup.domain.member.service.StudentIdCache;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.time.OperatingDayCalculator;
import com.checkup.checkup.support.MutableClock;
import com.checkup.checkup.domain.notification.service.NotificationService;

/**
 * 실제 PostgreSQL에서 자동 인증 출석이 학생·용도·운영일마다 한 번만 기록되고, 수동 수정·늦은 인증·시계 오차 규칙(REQ-ATT-002·007)과 출석 완료 알림 생성을 지키는지,
 * 관리자 수동 저장이 바뀌는 상태만 수정하고 최초 인증 시각을 남기며 수동 수정 전후 인증 순서(REQ-ATT-006, DEC-008)를 지키는지,
 * 수동 미출석이 그 용도의 출석 완료 알림을 지우는지(#148),
 * 학생 본인의 오늘 출석 조회가 용도·운영일 경계·수동 수정을 반영하는지,
 * 08:00 경계에 지난 운영일 출석을 지우고 지운 기록을 늦은 인증으로 되살리지 않는지 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({AttendanceService.class, NotificationService.class, StudentIdCache.class, OperatingDayCalculator.class,
        AttendanceServiceTest.ClockTestConfig.class})
class AttendanceServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 27);
    private static final Instant AT = Instant.parse("2026-09-27T12:00:00Z");
    /** 삭제 테스트용 시각. 같은 DB의 다른 출석 행을 지우지 않도록 실제 운영일보다 훨씬 이른 날을 쓴다. 2000-01-01 21:00 KST. */
    private static final Instant PURGE_AT = Instant.parse("2000-01-01T12:00:00Z");
    /** {@link #PURGE_AT} 다음 운영일. 2000-01-02 09:00 KST. */
    private static final Instant PURGE_NEXT_DAY = Instant.parse("2000-01-02T00:00:00Z");

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
                "INSERT INTO student (member_id, name, number, grade, class_number, student_number, dormitory_room, privacy_agreed_at, face_agreed_at) VALUES (?, '테스트학생', 1, 1, 1, 1101, 101, now(), now()) RETURNING id",
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
    @DisplayName("필수 동의가 없는 학생은 인증해도 기록하지 않고 403이다")
    void withoutConsentIsNotRecorded() {
        jdbcTemplate.update("UPDATE student SET face_agreed_at = NULL WHERE id = ?", studentId);

        assertThatThrownBy(() -> mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.QR))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(attendanceCount()).isZero();
    }

    @Test
    @DisplayName("호실이 없는 학생은 인증해도 기록하지 않고 403이다")
    void withoutRoomIsNotRecorded() {
        jdbcTemplate.update("UPDATE student SET dormitory_room = NULL WHERE id = ?", studentId);

        assertThatThrownBy(() -> mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.FACE))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(attendanceCount()).isZero();
    }

    @Test
    @DisplayName("없는 학생 id는 404 STUDENT_NOT_FOUND다")
    void unknownStudentIsNotFound() {
        assertThatThrownBy(() -> attendanceService.markAttended(
                -1L, AttendancePurpose.DORMITORY, AT, AttendanceMethod.QR))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STUDENT_NOT_FOUND));
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

    @Test
    @DisplayName("지난 운영일 출석을 지우면 오늘 운영일 출석만 남는다")
    void deleteExpiredKeepsOnlyToday() {
        clock.setInstant(PURGE_AT);
        mark(AttendancePurpose.DORMITORY, PURGE_AT, AttendanceMethod.QR);
        clock.setInstant(PURGE_NEXT_DAY);
        mark(AttendancePurpose.DORMITORY, PURGE_NEXT_DAY, AttendanceMethod.QR);

        int deleted = attendanceService.deleteExpired();

        assertThat(deleted).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList(
                "SELECT operating_day FROM attendance WHERE student_id = ?", LocalDate.class, studentId))
                .containsExactly(LocalDate.of(2000, 1, 2));
    }

    @Test
    @DisplayName("지난 운영일 출석을 지운 뒤 늦게 도착한 전날 인증은 기록을 되살리지 않는다")
    void lateVerificationDoesNotReviveDeletedAttendance() {
        clock.setInstant(PURGE_AT);
        mark(AttendancePurpose.DORMITORY, PURGE_AT, AttendanceMethod.QR);
        clock.setInstant(PURGE_NEXT_DAY);
        attendanceService.deleteExpired();

        AttendanceRecordResult result = mark(AttendancePurpose.DORMITORY, PURGE_AT, AttendanceMethod.FACE);

        assertThat(result).isEqualTo(AttendanceRecordResult.STALE);
        assertThat(rowCount()).isZero();
    }

    @Test
    @DisplayName("인증 기록이 없는 학생을 수동 출석으로 바꾸면 MANUAL 방식으로 출석이 되고 최초 인증 시각은 비어 있다")
    void manualAttendWithoutVerification() {
        saveManually(AttendancePurpose.DORMITORY, true);

        assertThat(attended(AttendancePurpose.DORMITORY, DAY)).isTrue();
        assertThat(method(AttendancePurpose.DORMITORY, DAY)).isEqualTo("MANUAL");
        assertThat(timestamp("first_verified_at")).isNull();
        assertThat(timestamp("manual_updated_at")).isEqualTo(AT);
        assertThat(notificationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("출석한 학생을 수동 미출석으로 바꾸면 최초 인증 시각과 방식은 남고 수정 시각을 기록하며 출석 완료 알림을 지운다")
    void manualAbsentKeepsFirstVerification() {
        mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.QR);
        clock.setInstant(AT.plusSeconds(600));

        saveManually(AttendancePurpose.DORMITORY, false);

        assertThat(attended(AttendancePurpose.DORMITORY, DAY)).isFalse();
        assertThat(firstVerifiedAt(AttendancePurpose.DORMITORY, DAY)).isEqualTo(AT);
        assertThat(method(AttendancePurpose.DORMITORY, DAY)).isEqualTo("QR");
        assertThat(timestamp("manual_updated_at")).isEqualTo(AT.plusSeconds(600));
        assertThat(notificationCount()).isZero();
    }

    @Test
    @DisplayName("수동 미출석을 다시 수동 출석으로 바꿔도 최초 인증 시각과 방식은 그대로이고 지웠던 알림을 다시 하나 만든다")
    void manualAttendAfterManualAbsentKeepsFirstVerification() {
        mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.QR);
        saveManually(AttendancePurpose.DORMITORY, false);
        clock.setInstant(AT.plusSeconds(600));

        saveManually(AttendancePurpose.DORMITORY, true);

        assertThat(attended(AttendancePurpose.DORMITORY, DAY)).isTrue();
        assertThat(firstVerifiedAt(AttendancePurpose.DORMITORY, DAY)).isEqualTo(AT);
        assertThat(method(AttendancePurpose.DORMITORY, DAY)).isEqualTo("QR");
        assertThat(timestamp("manual_updated_at")).isEqualTo(AT.plusSeconds(600));
        assertThat(notificationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 같은 상태인 학생은 수동 저장해도 바뀌지 않는다")
    void manualSaveWithSameStateChangesNothing() {
        saveManually(AttendancePurpose.DORMITORY, false);
        assertThat(rowCount()).isZero();

        mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.QR);
        clock.setInstant(AT.plusSeconds(600));
        saveManually(AttendancePurpose.DORMITORY, true);

        assertThat(timestamp("manual_updated_at")).isNull();
        assertThat(method(AttendancePurpose.DORMITORY, DAY)).isEqualTo("QR");
    }

    @Test
    @DisplayName("수동 미출석 저장 전에 발생한 늦은 인증은 무시하고 그 뒤 새 인증은 다시 출석으로 바꾼다")
    void manualAbsentOrdersLateAndNewVerifications() {
        mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.QR);
        clock.setInstant(AT.plusSeconds(600));
        saveManually(AttendancePurpose.DORMITORY, false);
        clock.setInstant(AT.plusSeconds(1200));

        AttendanceRecordResult late = mark(AttendancePurpose.DORMITORY, AT.plusSeconds(300), AttendanceMethod.FACE);
        assertThat(late).isEqualTo(AttendanceRecordResult.SUPERSEDED_BY_MANUAL);
        assertThat(attended(AttendancePurpose.DORMITORY, DAY)).isFalse();

        AttendanceRecordResult fresh = mark(AttendancePurpose.DORMITORY, AT.plusSeconds(1200), AttendanceMethod.FACE);
        assertThat(fresh).isEqualTo(AttendanceRecordResult.RECORDED);
        assertThat(attended(AttendancePurpose.DORMITORY, DAY)).isTrue();
        assertThat(firstVerifiedAt(AttendancePurpose.DORMITORY, DAY)).isEqualTo(AT);
    }

    @Test
    @DisplayName("수동 미출석은 요청한 용도의 출석 완료 알림만 지우고 다른 용도 알림은 남긴다")
    void manualAbsentDeletesOnlyThatPurposeNotification() {
        mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.QR);
        mark(AttendancePurpose.STUDY_ROOM, AT, AttendanceMethod.QR);

        saveManually(AttendancePurpose.DORMITORY, false);

        assertThat(jdbcTemplate.queryForList(
                "SELECT source_key FROM notification WHERE student_id = ?", String.class, studentId))
                .containsExactly("STUDY_ROOM:" + DAY);
    }

    @Test
    @DisplayName("이미 미출석인 학생을 다시 미출석으로 저장해도 다른 알림을 건드리지 않는다")
    void manualAbsentOnAbsentStudentKeepsNotifications() {
        mark(AttendancePurpose.STUDY_ROOM, AT, AttendanceMethod.QR);

        saveManually(AttendancePurpose.STUDY_ROOM, false);
        saveManually(AttendancePurpose.STUDY_ROOM, false);

        assertThat(notificationCount()).isZero();
        mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.QR);
        saveManually(AttendancePurpose.STUDY_ROOM, false);
        assertThat(notificationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("수동 저장은 요청한 용도의 출석만 바꾼다")
    void manualSaveIsSeparateByPurpose() {
        mark(AttendancePurpose.STUDY_ROOM, AT, AttendanceMethod.QR);

        saveManually(AttendancePurpose.DORMITORY, false);
        assertThat(attended(AttendancePurpose.STUDY_ROOM, DAY)).isTrue();
        assertThat(rowCount()).isEqualTo(1);

        saveManually(AttendancePurpose.DORMITORY, true);
        assertThat(attended(AttendancePurpose.DORMITORY, DAY)).isTrue();
        assertThat(method(AttendancePurpose.STUDY_ROOM, DAY)).isEqualTo("QR");
    }

    @Test
    @DisplayName("본인 출석 조회는 기록이 없으면 두 용도 모두 미출석이다")
    void myAttendanceWithoutRecordIsAbsent() {
        MyAttendanceResponse response = attendanceService.getMyToday(memberId);

        assertThat(response).isEqualTo(new MyAttendanceResponse(DAY,
                new MyAttendanceResponse.Status(false, null),
                new MyAttendanceResponse.Status(false, null)));
    }

    @Test
    @DisplayName("본인 출석 조회는 용도별로 출석 여부와 최초 인증 시각을 준다")
    void myAttendanceIsSeparateByPurpose() {
        mark(AttendancePurpose.STUDY_ROOM, AT, AttendanceMethod.QR);

        MyAttendanceResponse response = attendanceService.getMyToday(memberId);

        assertThat(response.dormitory()).isEqualTo(new MyAttendanceResponse.Status(false, null));
        assertThat(response.studyRoom()).isEqualTo(new MyAttendanceResponse.Status(true, AT));
    }

    @Test
    @DisplayName("본인 출석 조회는 오전 8시가 지나면 새 운영일의 미출석으로 바뀐다")
    void myAttendanceResetsAtOperatingDayBoundary() {
        mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.QR);

        clock.setInstant(Instant.parse("2026-09-27T22:59:59Z"));
        assertThat(attendanceService.getMyToday(memberId).operatingDay()).isEqualTo(DAY);
        assertThat(attendanceService.getMyToday(memberId).dormitory().attended()).isTrue();

        clock.setInstant(Instant.parse("2026-09-27T23:00:00Z"));
        MyAttendanceResponse nextDay = attendanceService.getMyToday(memberId);
        assertThat(nextDay.operatingDay()).isEqualTo(DAY.plusDays(1));
        assertThat(nextDay.dormitory()).isEqualTo(new MyAttendanceResponse.Status(false, null));
    }

    @Test
    @DisplayName("본인 출석 조회는 수동 미출석이면 미출석이고 수동으로만 출석했으면 인증 시각이 없다")
    void myAttendanceReflectsManualChanges() {
        mark(AttendancePurpose.DORMITORY, AT, AttendanceMethod.QR);
        saveManually(AttendancePurpose.DORMITORY, false);
        attendanceService.saveManually(Map.of(studentId, true), AttendancePurpose.STUDY_ROOM);

        MyAttendanceResponse response = attendanceService.getMyToday(memberId);

        assertThat(response.dormitory()).isEqualTo(new MyAttendanceResponse.Status(false, null));
        assertThat(response.studyRoom()).isEqualTo(new MyAttendanceResponse.Status(true, null));
    }

    @Test
    @DisplayName("학생 정보가 없는 회원의 본인 출석 조회는 MISSING_STUDENT_INFO다")
    void myAttendanceForNonStudentIsRejected() {
        long datagsmId = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
        Long teacherMemberId = jdbcTemplate.queryForObject(
                "INSERT INTO member (datagsm_id, name, role) VALUES (?, '테스트교사', 'ADMIN') RETURNING id",
                Long.class, datagsmId);
        try {
            assertThatThrownBy(() -> attendanceService.getMyToday(teacherMemberId))
                    .isInstanceOfSatisfying(CustomException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MISSING_STUDENT_INFO));
        } finally {
            jdbcTemplate.update("DELETE FROM member WHERE id = ?", teacherMemberId);
        }
    }

    private void saveManually(AttendancePurpose purpose, boolean attended) {
        attendanceService.saveManually(Map.of(studentId, attended), purpose);
    }

    private Instant timestamp(String column) {
        Timestamp value = jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM attendance WHERE student_id = ? AND purpose = 'DORMITORY' AND operating_day = ?",
                Timestamp.class, studentId, DAY);
        return value == null ? null : value.toInstant();
    }

    private int attendanceCount() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM attendance WHERE student_id = ?", Integer.class, studentId);
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
