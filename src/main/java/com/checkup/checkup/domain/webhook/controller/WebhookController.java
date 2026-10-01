package com.checkup.checkup.domain.webhook.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.checkup.checkup.domain.webhook.dto.response.StudentSyncResponse;
import com.checkup.checkup.domain.webhook.service.StudentManualSyncService;
import com.checkup.checkup.domain.webhook.service.WebhookService;
import com.checkup.checkup.domain.webhook.service.WebhookSignatureVerifier;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * DataGSM 학생 정보 동기화 API.
 *
 * - 웹훅 수신({@code POST /api/v1/webhook}): DataGSM이 로그인 없이 호출하며 {@code X-DataGSM-Signature} 서명으로 검증한다.
 *   DataGSM만 호출하는 API라 Swagger 문서에서는 숨긴다.
 * - 수동 동기화({@code POST /api/v1/webhook/sync}): 관리자가 로그인한 상태로 호출해 놓친 웹훅 변경을 복구한다.
 */
@RequiredArgsConstructor
@Tag(name = "DataGSM 동기화", description = "DataGSM 학생 정보 동기화. 웹훅 수신은 DataGSM 전용이라 문서에서 숨긴다")
@RestController
@RequestMapping("/api/v1/webhook")
public class WebhookController {

    private static final String SIGNATURE_HEADER = "X-DataGSM-Signature";
    private final WebhookSignatureVerifier verifier;
    private final WebhookService webhookService;
    private final StudentManualSyncService studentManualSyncService;

    /**
     * DataGSM 이벤트를 받는다. 서명은 JSON 파싱 전의 본문 원문으로 검증한다.
     *
     * @param body      요청 본문 원문
     * @param signature {@code X-DataGSM-Signature} 헤더 값
     * @throws CustomException 헤더가 없거나 서명이 일치하지 않으면 {@link ErrorCode#INVALID_WEBHOOK_SIGNATURE}(401)
     */
    @Hidden
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void receive(
            @RequestBody byte[] body,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature
    ) {
        if (!verifier.verify(body, signature)) throw new CustomException(ErrorCode.INVALID_WEBHOOK_SIGNATURE);
        webhookService.handle(body);
    }

    /**
     * DataGSM 학생 목록을 모두 받아 저장된 학생의 정보와 권한에 다시 반영한다. 관리자만 호출할 수 있다.
     *
     * @param memberId 세션의 회원 id
     * @return 받은 학생 수와 반영한 학생 수
     * @throws CustomException 관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403),
     *                         DataGSM 요청이 실패하면 {@link ErrorCode#DATAGSM_ERROR}(502)
     */
    @Operation(summary = "DataGSM 학생 수동 동기화", description = "관리자 전용. 놓친 웹훅 변경을 DataGSM 학생 목록으로 다시 반영한다. DataGSM 요청이 실패하면 502 DATAGSM_ERROR.")
    @PostMapping("/sync")
    public StudentSyncResponse syncStudents(@AuthenticationPrincipal Long memberId) {
        return studentManualSyncService.sync(memberId);
    }
}
