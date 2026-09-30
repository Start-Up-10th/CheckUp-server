package com.checkup.checkup.domain.consent.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.checkup.checkup.domain.consent.dto.request.ConsentRequest;
import com.checkup.checkup.domain.consent.service.ConsentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 학생 서비스 이용 동의 API(REQ-AUTH-004).
 */
@RestController
@RequestMapping("/api/v1/consent")
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentService consentService;

    /**
     * 로그인한 학생의 동의를 저장한다. 필수 두 항목에 동의하지 않았으면 400이다.
     * 다시 보내면 처음 동의 시각은 유지하고 공지 알림 수신만 바꾼다.
     *
     * @param memberId 세션의 회원 id
     * @param request  동의 항목
     */
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void agree(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody ConsentRequest request
    ) {
        consentService.agree(memberId, request.noticeAlarm());
    }
}
