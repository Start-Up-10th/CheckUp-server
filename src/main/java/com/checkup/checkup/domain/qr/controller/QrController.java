package com.checkup.checkup.domain.qr.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
}
