package com.checkup.checkup.domain.qr.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * QR 스캔 출석 요청. 학생은 요청 값이 아니라 로그인 세션으로 정한다.
 *
 * @param token QR 링크의 {@code #t=} 뒤 토큰
 */
public record QrScanRequest(
        @NotBlank String token
) {
}
