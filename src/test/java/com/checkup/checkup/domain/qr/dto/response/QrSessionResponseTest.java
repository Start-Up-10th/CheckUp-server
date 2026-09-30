package com.checkup.checkup.domain.qr.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.checkup.checkup.domain.qr.dto.QrSessionIssue;
import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;

class QrSessionResponseTest {

    private static final String TOKEN = "a".repeat(43);

    private final QrSessionIssue issue = new QrSessionIssue(
            "session-1",
            AttendancePurpose.DORMITORY,
            TOKEN,
            Instant.parse("2026-09-27T03:15:00Z"),
            Instant.parse("2026-09-27T03:01:00Z"),
            Instant.parse("2026-09-27T03:00:00Z")
    );

    @Test
    void qrUrl은_학생_웹_qr_경로와_fragment_토큰이다() {
        QrSessionResponse response = QrSessionResponse.of(issue, "https://checkup.example");

        assertThat(response.qrUrl()).isEqualTo("https://checkup.example/qr#t=" + TOKEN);
    }

    @Test
    void 웹_주소_끝의_슬래시는_한_번만_쓴다() {
        QrSessionResponse response = QrSessionResponse.of(issue, "http://localhost:3000/");

        assertThat(response.qrUrl()).isEqualTo("http://localhost:3000/qr#t=" + TOKEN);
    }

    @Test
    void 세션_정보를_그대로_옮긴다() {
        QrSessionResponse response = QrSessionResponse.of(issue, "http://localhost:3000");

        assertThat(response.sessionId()).isEqualTo("session-1");
        assertThat(response.purpose()).isEqualTo(AttendancePurpose.DORMITORY);
        assertThat(response.tokenExpiresAt()).isEqualTo(issue.tokenExpiresAt());
        assertThat(response.leaseExpiresAt()).isEqualTo(issue.leaseExpiresAt());
        assertThat(response.serverTime()).isEqualTo(issue.serverTime());
    }
}
