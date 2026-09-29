package com.checkup.checkup.domain.qr.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * QR 세션·토큰 수명과 QR 링크 설정.
 *
 * @param tokenTtl     토큰 유효 시간. 다음 08:00 KST를 넘지 않는다.
 * @param leaseTtl     heartbeat 없이 세션이 유지되는 시간
 * @param rotateBefore 토큰 만료 전 이 시간 안에 들어온 heartbeat에서 새 토큰을 발급한다
 * @param tokenRetention 토큰 기록을 만료 뒤에도 남기는 시간. 만료된 QR을 {@code EXPIRED}로 구분하는 데 쓴다.
 *                       Redis가 메모리 축출 정책(allkeys-lru 등)으로 기록을 먼저 지우면 {@code INVALID}로 보이므로
 *                       Redis는 {@code maxmemory-policy noeviction}으로 운영한다.
 * @param baseUrl      QR 링크의 학생 웹 주소. QR 값은 {@code {baseUrl}/qr#t=<토큰>}이다.
 *                     기본값이 없어, 비어 있으면 잘못된 QR을 내보내지 않도록 기동에 실패한다.
 */
@Validated
@ConfigurationProperties("checkup.qr")
public record QrProperties(
        Duration tokenTtl,
        Duration leaseTtl,
        Duration rotateBefore,
        Duration tokenRetention,
        @NotBlank String baseUrl
) {
}
