package com.checkup.checkup.domain.qr.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.qr.entity.QrSession;
import com.checkup.checkup.domain.qr.entity.QrToken;

/**
 * 실제 Redis에서 QR 세션 갱신이 종료된 세션을 되살리지 않는지, 토큰·세션 한 번 조회가 따로 읽은 값과 같은지 검증한다(#211).
 */
@DataRedisTest
@Import(QrSessionRepository.class)
class QrSessionRepositoryTest {

    private static final Duration TTL = Duration.ofSeconds(60);

    @Autowired
    private QrSessionRepository qrSessionRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final QrSession session = new QrSession(
            "session-1",
            1L,
            AttendancePurpose.DORMITORY,
            LocalDate.of(2026, 9, 27),
            "token-1",
            Instant.parse("2026-09-27T03:15:00Z"),
            Instant.parse("2026-09-27T03:01:00Z")
    );

    @BeforeEach
    void setUp() {
        Set<String> keys = redisTemplate.keys("qr:*");
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("세션이 있으면 갱신하고 true를 반환한다")
    void existingSessionIsUpdated() {
        qrSessionRepository.save(session, TTL);
        QrSession renewed = session.withLease(Instant.parse("2026-09-27T03:02:00Z"));

        boolean saved = qrSessionRepository.saveIfPresent(renewed, TTL);

        assertThat(saved).isTrue();
        assertThat(qrSessionRepository.findById("session-1")).contains(renewed);
        assertThat(qrSessionRepository.findSessionIdsByAdmin(1L)).containsExactly("session-1");
    }

    @Test
    @DisplayName("토큰과 세션을 한 번에 읽으면 따로 읽은 값과 같다")
    void findsTokenWithSessionInOneCall() {
        qrSessionRepository.save(session, TTL);
        QrToken token = new QrToken("token-1", "session-1", Instant.parse("2026-09-27T03:15:00Z"));
        qrSessionRepository.saveToken(token, TTL);

        QrSessionRepository.TokenLookup lookup = qrSessionRepository.findTokenWithSession("token-1");

        assertThat(lookup.token()).contains(token).isEqualTo(qrSessionRepository.findToken("token-1"));
        assertThat(lookup.session()).contains(session).isEqualTo(qrSessionRepository.findById("session-1"));
    }

    @Test
    @DisplayName("세션이 종료됐으면 토큰만 있고 세션은 비어 있다")
    void tokenWithoutSessionHasEmptySession() {
        qrSessionRepository.save(session, TTL);
        qrSessionRepository.saveToken(new QrToken("token-1", "session-1", Instant.parse("2026-09-27T03:15:00Z")), TTL);
        qrSessionRepository.delete(session);

        QrSessionRepository.TokenLookup lookup = qrSessionRepository.findTokenWithSession("token-1");

        assertThat(lookup.token()).isPresent();
        assertThat(lookup.session()).isEmpty();
    }

    @Test
    @DisplayName("발급하지 않은 토큰은 토큰도 세션도 비어 있다")
    void unknownTokenHasNothing() {
        qrSessionRepository.save(session, TTL);

        QrSessionRepository.TokenLookup lookup = qrSessionRepository.findTokenWithSession("unknown");

        assertThat(lookup.token()).isEmpty();
        assertThat(lookup.session()).isEmpty();
    }

    @Test
    @DisplayName("heartbeat가 읽은 뒤 close되면 세션을 되살리지 않는다")
    void closeAfterHeartbeatReadDoesNotRevive() {
        qrSessionRepository.save(session, TTL);
        QrSession readByHeartbeat = qrSessionRepository.findById("session-1").orElseThrow();

        qrSessionRepository.delete(readByHeartbeat);
        boolean saved = qrSessionRepository.saveIfPresent(readByHeartbeat.withLease(Instant.parse("2026-09-27T03:02:00Z")), TTL);

        assertThat(saved).isFalse();
        assertThat(qrSessionRepository.findById("session-1")).isEmpty();
        assertThat(qrSessionRepository.findSessionIdsByAdmin(1L)).isEmpty();
    }
}
