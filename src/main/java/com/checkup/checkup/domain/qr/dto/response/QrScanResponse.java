package com.checkup.checkup.domain.qr.dto.response;

import com.checkup.checkup.domain.qr.entity.QrScanResult;

/**
 * QR 스캔 출석 응답.
 *
 * @param result 판정 결과
 */
public record QrScanResponse(
        QrScanResult result
) {
}
