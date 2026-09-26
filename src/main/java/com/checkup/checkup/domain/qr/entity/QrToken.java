package com.checkup.checkup.domain.qr.entity;

import java.time.Instant;

/**
 * 발급된 QR 토큰. 교체된 뒤에도 원래 만료 시각까지만 조회된다.
 *
 * @param token     토큰 값
 * @param sessionId 토큰을 발급한 세션 ID
 * @param expiresAt 발급 때 정한 만료 시각. 이후 바뀌지 않는다.
 */
public record QrToken(
        String token,
        String sessionId,
        Instant expiresAt
) {
}
