package com.checkup.checkup.domain.qr.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.checkup.checkup.domain.qr.dto.request.QrScanRequest;
import com.checkup.checkup.domain.qr.dto.response.QrScanResponse;
import com.checkup.checkup.domain.qr.service.QrScanService;

import com.checkup.checkup.global.ratelimit.RateLimiter;
import java.time.Duration;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 학생 QR 스캔 출석 API(REQ-ATT-005, 하네스 DEC-018). 관리자 QR 화면 API와 권한이 달라 분리했다.
 */
@Tag(name = "QR 출석(학생)", description = "학생 QR 스캔 출석(REQ-ATT-005)")
@RestController
@RequestMapping("/api/v1/qr")
@RequiredArgsConstructor
public class QrScanController {

    private final QrScanService qrScanService;
    private final RateLimiter rateLimiter;

    /**
     * QR 토큰으로 로그인한 학생의 출석을 처리한다. 판정 결과는 모두 200으로 반환한다.
     *
     * @param memberId 세션의 회원 id
     * @param request  QR 토큰
     * @return 판정 결과. 로그인하지 않았으면 401, 학생이 아니면 403
     */
    @Operation(summary = "QR 스캔 출석", description = "판정 결과는 모두 200 + result(APPROVED·DUPLICATE·EXPIRED·CLOSED·INVALID)다. 학생이 아니면 403 MISSING_STUDENT_INFO, 필수 동의가 없으면 403 FACE_CONSENT_REQUIRED, 호실이 없으면 403 ROOM_NOT_ASSIGNED.")
    @PostMapping("/attendance")
    public QrScanResponse scan(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody QrScanRequest request
    ) {
        rateLimiter.check("qr-scan", memberId, 20, Duration.ofMinutes(1));
        return new QrScanResponse(qrScanService.scan(memberId, request.token()));
    }
}
