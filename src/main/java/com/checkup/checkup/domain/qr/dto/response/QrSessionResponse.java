package com.checkup.checkup.domain.qr.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

import com.checkup.checkup.domain.qr.dto.QrSessionIssue;
import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;

/**
 * QR 세션 생성·heartbeat 응답. 토큰 원문은 {@code qrUrl} 안에만 담는다.
 *
 * @param sessionId      이 페이지의 QR 세션 ID
 * @param purpose        출석 용도
 * @param qrUrl          QR로 그릴 값({@code {웹 주소}/qr#t=<토큰>})
 * @param tokenExpiresAt 현재 토큰의 만료 시각
 * @param leaseExpiresAt 다음 heartbeat가 없으면 세션이 끝나는 시각
 * @param serverTime     응답 시점의 서버 시각
 */
public record QrSessionResponse(
        @Schema(description = "이 페이지의 QR 세션 id. heartbeat·종료 경로에 쓴다", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") String sessionId,
        @Schema(description = "출석 용도. DORMITORY 또는 STUDY_ROOM", example = "DORMITORY") AttendancePurpose purpose,
        @Schema(description = "QR로 그릴 값({웹 주소}/qr#t=<토큰>)", example = "https://example.com/qr#t=token") String qrUrl,
        @Schema(description = "현재 토큰의 만료 시각") Instant tokenExpiresAt,
        @Schema(description = "다음 heartbeat가 없으면 세션이 끝나는 시각") Instant leaseExpiresAt,
        @Schema(description = "응답 시점의 서버 시각") Instant serverTime
) {

    /**
     * 서비스 결과와 학생 웹 주소로 응답을 만든다.
     */
    public static QrSessionResponse of(QrSessionIssue issue, String baseUrl) {
        return new QrSessionResponse(
                issue.sessionId(),
                issue.purpose(),
                qrUrl(baseUrl, issue.token()),
                issue.tokenExpiresAt(),
                issue.leaseExpiresAt(),
                issue.serverTime()
        );
    }

    private static String qrUrl(String baseUrl, String token) {
        String origin = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return origin + "/qr#t=" + token;
    }
}
