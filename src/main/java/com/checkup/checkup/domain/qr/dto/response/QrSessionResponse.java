package com.checkup.checkup.domain.qr.dto.response;

import java.time.Instant;

import com.checkup.checkup.domain.qr.dto.QrSessionIssue;
import com.checkup.checkup.domain.qr.entity.QrPurpose;

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
        String sessionId,
        QrPurpose purpose,
        String qrUrl,
        Instant tokenExpiresAt,
        Instant leaseExpiresAt,
        Instant serverTime
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
