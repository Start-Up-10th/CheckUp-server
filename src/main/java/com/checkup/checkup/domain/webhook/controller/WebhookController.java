package com.checkup.checkup.domain.webhook.controller;

import com.checkup.checkup.domain.webhook.service.WebhookService;
import com.checkup.checkup.domain.webhook.service.WebhookSignatureVerifier;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * DataGSM 웹훅 수신 API. 로그인 없이 호출되며 {@code X-DataGSM-Signature} 서명으로 요청을 검증한다.
 * DataGSM만 호출하는 API라 Swagger 문서에서는 숨긴다.
 */
@Hidden
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/webhook")
public class WebhookController {

    private static final String SIGNATURE_HEADER = "X-DataGSM-Signature";
    private final WebhookSignatureVerifier verifier;
    private final WebhookService webhookService;

    /**
     * DataGSM 이벤트를 받는다. 서명은 JSON 파싱 전의 본문 원문으로 검증한다.
     *
     * @param body      요청 본문 원문
     * @param signature {@code X-DataGSM-Signature} 헤더 값
     * @throws CustomException 헤더가 없거나 서명이 일치하지 않으면 {@link ErrorCode#INVALID_WEBHOOK_SIGNATURE}(401)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void receive(
            @RequestBody byte[] body,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature
    ) {
        if (!verifier.verify(body, signature)) throw new CustomException(ErrorCode.INVALID_WEBHOOK_SIGNATURE);
        webhookService.handle(body);
    }
}
