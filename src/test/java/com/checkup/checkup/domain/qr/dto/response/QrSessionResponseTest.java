package com.checkup.checkup.domain.qr.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("qrUrl은 학생 웹 qr 경로와 fragment 토큰이다")
    void qrUrlUsesWebQrPathAndFragmentToken() {
        QrSessionResponse response = QrSessionResponse.of(issue, "https://checkup.example");

        assertThat(response.qrUrl()).isEqualTo("https://checkup.example/qr#t=" + TOKEN);
    }

    @Test
    @DisplayName("웹 주소 끝의 슬래시는 한 번만 쓴다")
    void trailingSlashOfWebUrlIsUsedOnce() {
        QrSessionResponse response = QrSessionResponse.of(issue, "http://localhost:3000/");

        assertThat(response.qrUrl()).isEqualTo("http://localhost:3000/qr#t=" + TOKEN);
    }

    @Test
    @DisplayName("세션 정보를 그대로 옮긴다")
    void copiesSessionFields() {
        QrSessionResponse response = QrSessionResponse.of(issue, "http://localhost:3000");

        assertThat(response.sessionId()).isEqualTo("session-1");
        assertThat(response.purpose()).isEqualTo(AttendancePurpose.DORMITORY);
        assertThat(response.tokenExpiresAt()).isEqualTo(issue.tokenExpiresAt());
        assertThat(response.leaseExpiresAt()).isEqualTo(issue.leaseExpiresAt());
        assertThat(response.serverTime()).isEqualTo(issue.serverTime());
    }
}
