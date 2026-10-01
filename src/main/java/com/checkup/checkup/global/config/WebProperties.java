package com.checkup.checkup.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * 웹 프런트 설정.
 *
 * @param baseUrl 웹 주소(예: {@code https://checkup.example}). 로그인 후 서버가 브라우저를 돌려보낼 때 쓴다.
 *                기본값이 없어, 비어 있으면 엉뚱한 주소로 보내지 않도록 기동에 실패한다.
 */
@Validated
@ConfigurationProperties("checkup.web")
public record WebProperties(
        @NotBlank String baseUrl
) {
}
