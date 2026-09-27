package com.checkup.checkup.domain.qr.dto;

import java.time.Instant;

import com.checkup.checkup.domain.qr.entity.QrPurpose;
import com.checkup.checkup.domain.qr.entity.QrSession;

/**
 * 관리자 QR 화면에 내려줄 현재 세션 상태.
 *
 * @param sessionId       세션 ID
 * @param purpose         출석 용도
 * @param token           현재 표시할 토큰
 * @param tokenExpiresAt  현재 토큰의 만료 시각
 * @param leaseExpiresAt  다음 heartbeat가 없으면 세션이 끝나는 시각
 * @param serverTime      응답 시점의 서버 시각
 */
public record QrSessionIssue(
        String sessionId,
        QrPurpose purpose,
        String token,
        Instant tokenExpiresAt,
        Instant leaseExpiresAt,
        Instant serverTime
) {

    public static QrSessionIssue of(QrSession session, Instant serverTime) {
        return new QrSessionIssue(
                session.id(),
                session.purpose(),
                session.token(),
                session.tokenExpiresAt(),
                session.leaseExpiresAt(),
                serverTime
        );
    }
}
