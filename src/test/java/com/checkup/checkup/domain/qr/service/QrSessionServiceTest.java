package com.checkup.checkup.domain.qr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.checkup.checkup.domain.qr.config.QrConfig;
import com.checkup.checkup.domain.qr.dto.QrSessionIssue;
import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.qr.repository.QrSessionRepository;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.time.OperatingDayCalculator;
import com.checkup.checkup.support.MutableClock;

/**
 * QR 세션과 토큰의 수명(15분 토큰, lease, 08:00 경계)과 페이지·관리자별 독립 종료를 검증한다(REQ-ATT-003·004).
 */
@DataRedisTest
@Import({
        QrSessionService.class,
        QrSessionRepository.class,
        QrTokenGenerator.class,
        OperatingDayCalculator.class,
        QrConfig.class,
        QrSessionServiceTest.ClockTestConfig.class
})
class QrSessionServiceTest {

    private static final Long ADMIN_A = 1L;
    private static final Long ADMIN_B = 2L;
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(20);

    @TestConfiguration
    static class ClockTestConfig {
        @Bean
        MutableClock clock() {
            return new MutableClock(Instant.EPOCH);
        }
    }

    @Autowired
    private QrSessionService qrSessionService;

    @Autowired
    private QrSessionRepository qrSessionRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        Set<String> keys = redisTemplate.keys("qr:*");
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        clock.setInstant(kst(2026, 9, 26, 12, 0));
    }

    @Test
    @DisplayName("세션을 만들면 15분짜리 토큰과 lease를 발급한다")
    void createIssues15MinuteTokenAndLease() {
        Instant now = clock.instant();

        QrSessionIssue issue = qrSessionService.create(ADMIN_A, AttendancePurpose.DORMITORY);

        assertThat(issue.sessionId()).isNotBlank();
        assertThat(issue.purpose()).isEqualTo(AttendancePurpose.DORMITORY);
        assertThat(issue.token()).isNotBlank();
        assertThat(issue.tokenExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(15)));
        assertThat(issue.leaseExpiresAt()).isEqualTo(now.plus(Duration.ofSeconds(60)));
        assertThat(issue.serverTime()).isEqualTo(now);
    }

    @Test
    @DisplayName("토큰 기록은 만료 뒤에도 보관 시간만큼 남는다")
    void tokenRecordIsRetainedAfterExpiry() {
        QrSessionIssue issue = qrSessionService.create(ADMIN_A, AttendancePurpose.DORMITORY);

        Long ttlSeconds = redisTemplate.getExpire("qr:token:" + issue.token());

        assertThat(ttlSeconds).isBetween(
                Duration.ofMinutes(15).plusHours(1).minusSeconds(5).toSeconds(),
                Duration.ofMinutes(15).plusHours(1).toSeconds());
    }

    @Test
    @DisplayName("페이지마다 다른 세션과 토큰을 만든다")
    void eachPageGetsOwnSessionAndToken() {
        QrSessionIssue first = qrSessionService.create(ADMIN_A, AttendancePurpose.DORMITORY);
        QrSessionIssue second = qrSessionService.create(ADMIN_A, AttendancePurpose.DORMITORY);

        assertThat(first.sessionId()).isNotEqualTo(second.sessionId());
        assertThat(first.token()).isNotEqualTo(second.token());
    }

    @Test
    @DisplayName("만료가 멀면 heartbeat는 같은 토큰을 유지하고 lease만 연장한다")
    void heartbeatKeepsTokenWhenExpiryIsFar() {
        QrSessionIssue created = qrSessionService.create(ADMIN_A, AttendancePurpose.DORMITORY);
        clock.advance(HEARTBEAT_INTERVAL);

        QrSessionIssue beat = qrSessionService.heartbeat(ADMIN_A, created.sessionId());

        assertThat(beat.token()).isEqualTo(created.token());
        assertThat(beat.tokenExpiresAt()).isEqualTo(created.tokenExpiresAt());
        assertThat(beat.leaseExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("만료가 가까우면 새 토큰을 발급하고 이전 토큰의 만료는 그대로 둔다")
    void heartbeatRotatesTokenNearExpiry() {
        QrSessionIssue created = qrSessionService.create(ADMIN_A, AttendancePurpose.DORMITORY);

        QrSessionIssue beat = heartbeatUntil(created.sessionId(), created.tokenExpiresAt().minusSeconds(30));

        assertThat(beat.token()).isNotEqualTo(created.token());
        assertThat(beat.tokenExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));
        assertThat(qrSessionRepository.findToken(created.token()).orElseThrow().expiresAt())
                .isEqualTo(created.tokenExpiresAt());
    }

    @Test
    @DisplayName("오전 8시 직전에 발급한 토큰은 8시에 만료된다")
    void tokenIssuedBefore8ExpiresAt8() {
        clock.setInstant(kst(2026, 9, 26, 7, 55));

        QrSessionIssue issue = qrSessionService.create(ADMIN_A, AttendancePurpose.STUDY_ROOM);

        assertThat(issue.tokenExpiresAt()).isEqualTo(kst(2026, 9, 26, 8, 0));
    }

    @Test
    @DisplayName("오전 8시 직전에는 같은 만료의 토큰을 다시 발급하지 않는다")
    void doesNotReissueSameExpiryBefore8() {
        clock.setInstant(kst(2026, 9, 26, 7, 55));
        QrSessionIssue created = qrSessionService.create(ADMIN_A, AttendancePurpose.DORMITORY);

        QrSessionIssue beat = heartbeatUntil(created.sessionId(), kst(2026, 9, 26, 7, 59, 50));

        assertThat(beat.token()).isEqualTo(created.token());
    }

    @Test
    @DisplayName("오전 8시가 지나면 새 운영일 토큰으로 교체한다")
    void rotatesTokenAfter8() {
        clock.setInstant(kst(2026, 9, 26, 7, 55));
        QrSessionIssue created = qrSessionService.create(ADMIN_A, AttendancePurpose.DORMITORY);

        QrSessionIssue beat = heartbeatUntil(created.sessionId(), kst(2026, 9, 26, 8, 0));

        assertThat(beat.token()).isNotEqualTo(created.token());
        assertThat(beat.tokenExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("heartbeat가 끊겨 lease가 지나면 세션을 찾을 수 없다")
    void sessionIsGoneAfterLeaseExpires() {
        QrSessionIssue created = qrSessionService.create(ADMIN_A, AttendancePurpose.DORMITORY);
        clock.advance(Duration.ofSeconds(60));

        assertThatThrownBy(() -> qrSessionService.heartbeat(ADMIN_A, created.sessionId()))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.QR_SESSION_NOT_FOUND));
    }

    @Test
    @DisplayName("다른 관리자는 세션을 유지하거나 종료할 수 없다")
    void otherAdminCannotKeepOrCloseSession() {
        QrSessionIssue created = qrSessionService.create(ADMIN_A, AttendancePurpose.DORMITORY);

        assertThatThrownBy(() -> qrSessionService.heartbeat(ADMIN_B, created.sessionId()))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.QR_SESSION_NOT_FOUND));

        qrSessionService.close(ADMIN_B, created.sessionId());

        assertThat(qrSessionService.heartbeat(ADMIN_A, created.sessionId()).sessionId())
                .isEqualTo(created.sessionId());
    }

    @Test
    @DisplayName("세션을 종료해도 다른 탭과 다른 관리자의 세션은 유지된다")
    void closeKeepsOtherSessions() {
        QrSessionIssue closed = qrSessionService.create(ADMIN_A, AttendancePurpose.DORMITORY);
        QrSessionIssue otherTab = qrSessionService.create(ADMIN_A, AttendancePurpose.DORMITORY);
        QrSessionIssue otherAdmin = qrSessionService.create(ADMIN_B, AttendancePurpose.DORMITORY);

        qrSessionService.close(ADMIN_A, closed.sessionId());

        assertThatThrownBy(() -> qrSessionService.heartbeat(ADMIN_A, closed.sessionId()))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.QR_SESSION_NOT_FOUND));
        assertThat(qrSessionService.heartbeat(ADMIN_A, otherTab.sessionId()).sessionId())
                .isEqualTo(otherTab.sessionId());
        assertThat(qrSessionService.heartbeat(ADMIN_B, otherAdmin.sessionId()).sessionId())
                .isEqualTo(otherAdmin.sessionId());
    }

    @Test
    @DisplayName("이미 종료된 세션을 다시 종료해도 오류가 없다")
    void closingClosedSessionIsNoOp() {
        QrSessionIssue created = qrSessionService.create(ADMIN_A, AttendancePurpose.DORMITORY);

        qrSessionService.close(ADMIN_A, created.sessionId());
        qrSessionService.close(ADMIN_A, created.sessionId());

        assertThat(qrSessionRepository.findById(created.sessionId())).isEmpty();
    }

    @Test
    @DisplayName("전체 종료는 그 관리자의 세션만 모두 끝낸다")
    void closeAllEndsOnlyThatAdminsSessions() {
        QrSessionIssue first = qrSessionService.create(ADMIN_A, AttendancePurpose.DORMITORY);
        QrSessionIssue second = qrSessionService.create(ADMIN_A, AttendancePurpose.STUDY_ROOM);
        QrSessionIssue otherAdmin = qrSessionService.create(ADMIN_B, AttendancePurpose.DORMITORY);

        qrSessionService.closeAll(ADMIN_A);

        assertThat(qrSessionRepository.findById(first.sessionId())).isEmpty();
        assertThat(qrSessionRepository.findById(second.sessionId())).isEmpty();
        assertThat(qrSessionRepository.findById(otherAdmin.sessionId())).isPresent();
    }

    private QrSessionIssue heartbeatUntil(String sessionId, Instant target) {
        QrSessionIssue beat = null;
        while (clock.instant().isBefore(target)) {
            Duration remaining = Duration.between(clock.instant(), target);
            clock.advance(remaining.compareTo(HEARTBEAT_INTERVAL) < 0 ? remaining : HEARTBEAT_INTERVAL);
            beat = qrSessionService.heartbeat(ADMIN_A, sessionId);
        }
        return beat;
    }

    private static Instant kst(int year, int month, int day, int hour, int minute) {
        return kst(year, month, day, hour, minute, 0);
    }

    private static Instant kst(int year, int month, int day, int hour, int minute, int second) {
        return LocalDateTime.of(year, month, day, hour, minute, second)
                .atZone(OperatingDayCalculator.ZONE)
                .toInstant();
    }
}
