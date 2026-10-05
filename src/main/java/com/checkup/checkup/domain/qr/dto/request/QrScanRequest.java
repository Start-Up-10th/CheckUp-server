package com.checkup.checkup.domain.qr.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * QR 스캔 출석 요청. 학생은 요청 값이 아니라 로그인 세션으로 정한다.
 *
 * @param token QR 링크의 {@code #t=} 뒤 토큰
 */
public record QrScanRequest(
        @Schema(description = "QR 링크의 #t= 뒤 토큰") @NotBlank String token
) {
}
