package com.checkup.checkup.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * DataGSM OAuth 콜백 쿼리 파라미터.
 *
 * @param code  DataGSM 인가 코드
 * @param state 로그인 요청 때 발급한 state
 */
public record OAuthCallbackRequest(
        @NotBlank(message = "code가 필요합니다.") String code,
        @NotBlank(message = "state가 필요합니다.") String state
) {}
