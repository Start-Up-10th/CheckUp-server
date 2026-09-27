package com.checkup.checkup.domain.qr.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * QR 세션·토큰 수명과 QR 링크 설정.
 *
 * @param tokenTtl     토큰 유효 시간. 다음 08:00 KST를 넘지 않는다.
 * @param leaseTtl     heartbeat 없이 세션이 유지되는 시간
 * @param rotateBefore 토큰 만료 전 이 시간 안에 들어온 heartbeat에서 새 토큰을 발급한다
 * @param baseUrl      QR 링크의 학생 웹 주소. QR 값은 {@code {baseUrl}/qr#t=<토큰>}이다.
 */
@ConfigurationProperties("checkup.qr")
public record QrProperties(
        Duration tokenTtl,
        Duration leaseTtl,
        Duration rotateBefore,
        String baseUrl
) {
}
