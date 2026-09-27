package com.checkup.checkup.domain.qr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.checkup.checkup.domain.qr.config.QrConfig;
import com.checkup.checkup.domain.qr.dto.QrSessionIssue;
import com.checkup.checkup.domain.qr.entity.QrPurpose;
import com.checkup.checkup.domain.qr.repository.QrSessionRepository;
import com.checkup.checkup.global.time.OperatingDayCalculator;
import com.checkup.checkup.support.MutableClock;

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
    void 세션을_만들면_15분짜리_토큰과_lease를_발급한다() {
        Instant now = clock.instant();

        QrSessionIssue issue = qrSessionService.create(ADMIN_A, QrPurpose.DORMITORY);

        assertThat(issue.sessionId()).isNotBlank();
        assertThat(issue.purpose()).isEqualTo(QrPurpose.DORMITORY);
        assertThat(issue.token()).isNotBlank();
        assertThat(issue.tokenExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(15)));
        assertThat(issue.leaseExpiresAt()).isEqualTo(now.plus(Duration.ofSeconds(60)));
        assertThat(issue.serverTime()).isEqualTo(now);
    }

    @Test
    void 페이지마다_다른_세션과_토큰을_만든다() {
        QrSessionIssue first = qrSessionService.create(ADMIN_A, QrPurpose.DORMITORY);
        QrSessionIssue second = qrSessionService.create(ADMIN_A, QrPurpose.DORMITORY);

        assertThat(first.sessionId()).isNotEqualTo(second.sessionId());
        assertThat(first.token()).isNotEqualTo(second.token());
    }

    @Test
    void 만료가_멀면_heartbeat는_같은_토큰을_유지하고_lease만_연장한다() {
        QrSessionIssue created = qrSessionService.create(ADMIN_A, QrPurpose.DORMITORY);
        clock.advance(HEARTBEAT_INTERVAL);

        QrSessionIssue beat = qrSessionService.heartbeat(ADMIN_A, created.sessionId());

        assertThat(beat.token()).isEqualTo(created.token());
        assertThat(beat.tokenExpiresAt()).isEqualTo(created.tokenExpiresAt());
        assertThat(beat.leaseExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofSeconds(60)));
    }

    @Test
    void 만료가_가까우면_새_토큰을_발급하고_이전_토큰의_만료는_그대로_둔다() {
        QrSessionIssue created = qrSessionService.create(ADMIN_A, QrPurpose.DORMITORY);

        QrSessionIssue beat = heartbeatUntil(created.sessionId(), created.tokenExpiresAt().minusSeconds(30));

        assertThat(beat.token()).isNotEqualTo(created.token());
        assertThat(beat.tokenExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));
        assertThat(qrSessionRepository.findToken(created.token()).orElseThrow().expiresAt())
                .isEqualTo(created.tokenExpiresAt());
    }

    @Test
    void 오전_8시_직전에_발급한_토큰은_8시에_만료된다() {
        clock.setInstant(kst(2026, 9, 26, 7, 55));

        QrSessionIssue issue = qrSessionService.create(ADMIN_A, QrPurpose.STUDY_ROOM);

        assertThat(issue.tokenExpiresAt()).isEqualTo(kst(2026, 9, 26, 8, 0));
    }

    @Test
    void 오전_8시_직전에는_같은_만료의_토큰을_다시_발급하지_않는다() {
        clock.setInstant(kst(2026, 9, 26, 7, 55));
        QrSessionIssue created = qrSessionService.create(ADMIN_A, QrPurpose.DORMITORY);

        QrSessionIssue beat = heartbeatUntil(created.sessionId(), kst(2026, 9, 26, 7, 59, 50));

        assertThat(beat.token()).isEqualTo(created.token());
    }

    @Test
    void 오전_8시가_지나면_새_운영일_토큰으로_교체한다() {
        clock.setInstant(kst(2026, 9, 26, 7, 55));
        QrSessionIssue created = qrSessionService.create(ADMIN_A, QrPurpose.DORMITORY);

        QrSessionIssue beat = heartbeatUntil(created.sessionId(), kst(2026, 9, 26, 8, 0));

        assertThat(beat.token()).isNotEqualTo(created.token());
        assertThat(beat.tokenExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));
    }

    @Test
    void heartbeat가_끊겨_lease가_지나면_세션을_찾을_수_없다() {
        QrSessionIssue created = qrSessionService.create(ADMIN_A, QrPurpose.DORMITORY);
        clock.advance(Duration.ofSeconds(60));

        assertThatThrownBy(() -> qrSessionService.heartbeat(ADMIN_A, created.sessionId()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void 다른_관리자는_세션을_유지하거나_종료할_수_없다() {
        QrSessionIssue created = qrSessionService.create(ADMIN_A, QrPurpose.DORMITORY);

        assertThatThrownBy(() -> qrSessionService.heartbeat(ADMIN_B, created.sessionId()))
                .isInstanceOf(ResponseStatusException.class);

        qrSessionService.close(ADMIN_B, created.sessionId());

        assertThat(qrSessionService.heartbeat(ADMIN_A, created.sessionId()).sessionId())
                .isEqualTo(created.sessionId());
    }

    @Test
    void 세션을_종료해도_다른_탭과_다른_관리자의_세션은_유지된다() {
        QrSessionIssue closed = qrSessionService.create(ADMIN_A, QrPurpose.DORMITORY);
        QrSessionIssue otherTab = qrSessionService.create(ADMIN_A, QrPurpose.DORMITORY);
        QrSessionIssue otherAdmin = qrSessionService.create(ADMIN_B, QrPurpose.DORMITORY);

        qrSessionService.close(ADMIN_A, closed.sessionId());

        assertThatThrownBy(() -> qrSessionService.heartbeat(ADMIN_A, closed.sessionId()))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(qrSessionService.heartbeat(ADMIN_A, otherTab.sessionId()).sessionId())
                .isEqualTo(otherTab.sessionId());
        assertThat(qrSessionService.heartbeat(ADMIN_B, otherAdmin.sessionId()).sessionId())
                .isEqualTo(otherAdmin.sessionId());
    }

    @Test
    void 이미_종료된_세션을_다시_종료해도_오류가_없다() {
        QrSessionIssue created = qrSessionService.create(ADMIN_A, QrPurpose.DORMITORY);

        qrSessionService.close(ADMIN_A, created.sessionId());
        qrSessionService.close(ADMIN_A, created.sessionId());

        assertThat(qrSessionRepository.findById(created.sessionId())).isEmpty();
    }

    @Test
    void 전체_종료는_그_관리자의_세션만_모두_끝낸다() {
        QrSessionIssue first = qrSessionService.create(ADMIN_A, QrPurpose.DORMITORY);
        QrSessionIssue second = qrSessionService.create(ADMIN_A, QrPurpose.STUDY_ROOM);
        QrSessionIssue otherAdmin = qrSessionService.create(ADMIN_B, QrPurpose.DORMITORY);

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
