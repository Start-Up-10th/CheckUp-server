package com.checkup.checkup.global.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.http.HttpHeaders;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 지표 경로 요청의 Bearer 토큰을 확인한다.
 *
 * Prometheus는 세션 로그인을 할 수 없고, 운영 서버의 8080 포트는 외부에도 열려 있어 토큰으로 수집기만 통과시킨다.
 * 토큰이 비어 있으면 지표 경로가 그대로 열리므로 만들 때 실패시켜 서버가 시작하지 않게 한다.
 */
class MetricsTokenVerifier {

    private static final String BEARER_PREFIX = "Bearer ";

    private final byte[] expectedToken;

    MetricsTokenVerifier(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("METRICS_ENABLED=true이면 METRICS_TOKEN을 설정해야 합니다.");
        }
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 요청의 Authorization 헤더가 설정한 토큰과 같은지 확인한다. 비교 시간으로 토큰이 드러나지 않게 고정 시간 비교를 쓴다.
     */
    boolean matches(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return false;
        }
        byte[] received = header.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedToken, received);
    }
}
