package com.checkup.checkup.domain.qr.dto.response;

import com.checkup.checkup.domain.qr.entity.QrScanResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * QR 스캔 출석 응답.
 *
 * @param result 판정 결과
 */
public record QrScanResponse(
        @Schema(description = "판정 결과. APPROVED(출석 처리), DUPLICATE(이미 출석), EXPIRED(토큰 만료), CLOSED(QR 세션 종료), INVALID(알 수 없는 토큰). 모두 200으로 응답한다", example = "APPROVED") QrScanResult result
) {
}
