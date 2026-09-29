package com.checkup.checkup.domain.qr.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.checkup.checkup.domain.qr.config.QrProperties;
import com.checkup.checkup.domain.qr.dto.request.QrCreateRequest;
import com.checkup.checkup.domain.qr.dto.response.QrSessionResponse;
import com.checkup.checkup.domain.qr.service.QrSessionService;
import com.checkup.checkup.global.security.AdminVerifier;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 관리자 QR 화면 API(REQ-ATT-003·004, 하네스 DEC-018).
 */
@RestController
@RequestMapping("/api/v1/qr")
@RequiredArgsConstructor
public class QrController {

    private final QrSessionService qrSessionService;
    private final AdminVerifier adminVerifier;
    private final QrProperties qrProperties;

    /**
     * 페이지마다 새 QR 세션을 만든다.
     *
     * @param memberId 세션의 회원 id
     * @return 201과 세션·QR 링크
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QrSessionResponse create(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody QrCreateRequest request
    ) {
        adminVerifier.verify(memberId);
        return QrSessionResponse.of(qrSessionService.create(memberId, request.purpose()), qrProperties.baseUrl());
    }

    /**
     * lease를 연장하고 현재 QR 링크를 반환한다. 토큰 만료가 가까우면 새 토큰으로 바뀐다.
     *
     * @param memberId  세션의 회원 id
     * @param sessionId QR 세션 ID
     * @return 세션·QR 링크. 세션이 없거나 끝났거나 다른 관리자의 세션이면 404
     */
    @PostMapping("/{sessionId}/heartbeat")
    public QrSessionResponse heartbeat(
            @AuthenticationPrincipal Long memberId,
            @PathVariable String sessionId
    ) {
        adminVerifier.verify(memberId);
        return QrSessionResponse.of(qrSessionService.heartbeat(memberId, sessionId), qrProperties.baseUrl());
    }

    /**
     * 이 페이지의 QR 세션만 종료한다. 페이지 이탈 때 {@code sendBeacon}으로 호출할 수 있다.
     *
     * @param memberId  세션의 회원 id
     * @param sessionId QR 세션 ID
     */
    @PostMapping("/{sessionId}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(
            @AuthenticationPrincipal Long memberId,
            @PathVariable String sessionId
    ) {
        adminVerifier.verify(memberId);
        qrSessionService.close(memberId, sessionId);
    }
}
