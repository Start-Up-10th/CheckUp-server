package com.checkup.checkup.domain.qr.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * QR 세션·토큰 수명 설정.
 *
 * @param tokenTtl     토큰 유효 시간. 다음 08:00 KST를 넘지 않는다.
 * @param leaseTtl     heartbeat 없이 세션이 유지되는 시간
 * @param rotateBefore 토큰 만료 전 이 시간 안에 들어온 heartbeat에서 새 토큰을 발급한다
 */
@ConfigurationProperties("checkup.qr")
public record QrProperties(
        Duration tokenTtl,
        Duration leaseTtl,
        Duration rotateBefore
) {
}
