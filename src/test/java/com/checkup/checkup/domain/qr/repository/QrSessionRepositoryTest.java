package com.checkup.checkup.domain.qr.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.checkup.checkup.domain.qr.entity.QrPurpose;
import com.checkup.checkup.domain.qr.entity.QrSession;

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
            QrPurpose.DORMITORY,
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
    void 세션이_있으면_갱신하고_true를_반환한다() {
        qrSessionRepository.save(session, TTL);
        QrSession renewed = session.withLease(Instant.parse("2026-09-27T03:02:00Z"));

        boolean saved = qrSessionRepository.saveIfPresent(renewed, TTL);

        assertThat(saved).isTrue();
        assertThat(qrSessionRepository.findById("session-1")).contains(renewed);
        assertThat(qrSessionRepository.findSessionIdsByAdmin(1L)).containsExactly("session-1");
    }

    @Test
    void heartbeat가_읽은_뒤_close되면_세션을_되살리지_않는다() {
        qrSessionRepository.save(session, TTL);
        QrSession readByHeartbeat = qrSessionRepository.findById("session-1").orElseThrow();

        qrSessionRepository.delete(readByHeartbeat);
        boolean saved = qrSessionRepository.saveIfPresent(readByHeartbeat.withLease(Instant.parse("2026-09-27T03:02:00Z")), TTL);

        assertThat(saved).isFalse();
        assertThat(qrSessionRepository.findById("session-1")).isEmpty();
        assertThat(qrSessionRepository.findSessionIdsByAdmin(1L)).isEmpty();
    }
}
