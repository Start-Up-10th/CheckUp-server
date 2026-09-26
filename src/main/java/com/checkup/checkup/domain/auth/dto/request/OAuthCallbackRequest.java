package com.checkup.checkup.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OAuthCallbackRequest(
        @NotBlank(message = "code가 필요합니다.") String code,
        @NotBlank(message = "state가 필요합니다.") String state
) {}
