package com.checkup.checkup.domain.auth.service;

/**
 * 로그인 시작 때 저장해 콜백에서 꺼내는 값.
 *
 * @param codeVerifier PKCE code verifier
 * @param redirectPath 로그인 후 돌아갈 웹 경로. {@link LoginRedirectPath#sanitize}를 거친 값이다.
 */
public record OAuthState(
        String codeVerifier,
        String redirectPath
) {
}
