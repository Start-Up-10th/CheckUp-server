package com.checkup.checkup.domain.webhook.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/**
 * 실제 PostgreSQL에서 웹훅 이벤트 ID가 {@code ON CONFLICT DO NOTHING}으로 한 번만 기록되는지 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WebhookEventLogRepositoryTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-06-23T05:22:00Z");

    @Autowired
    private WebhookEventLogRepository webhookEventLogRepository;

    @Test
    @DisplayName("처음 온 이벤트 ID는 1을 반환하고 기록한다")
    void newEventIsRecorded() {
        int recorded = webhookEventLogRepository.record("evt_new", RECEIVED_AT);

        assertThat(recorded).isEqualTo(1);
        assertThat(webhookEventLogRepository.findById("evt_new"))
                .hasValueSatisfying(log -> assertThat(log.getReceivedAt()).isEqualTo(RECEIVED_AT));
    }

    @Test
    @DisplayName("이미 기록한 이벤트 ID는 오류 없이 0을 반환하고 처음 기록을 유지한다")
    void duplicateEventReturnsZero() {
        webhookEventLogRepository.record("evt_dup", RECEIVED_AT);

        int recorded = webhookEventLogRepository.record("evt_dup", RECEIVED_AT.plusSeconds(10));

        assertThat(recorded).isZero();
        assertThat(webhookEventLogRepository.findById("evt_dup"))
                .hasValueSatisfying(log -> assertThat(log.getReceivedAt()).isEqualTo(RECEIVED_AT));
    }
}
