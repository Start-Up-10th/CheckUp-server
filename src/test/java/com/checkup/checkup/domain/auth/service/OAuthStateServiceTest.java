package com.checkup.checkup.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * OAuth state에 code_verifier와 돌아갈 경로가 함께 저장되고, 한 번만 꺼낼 수 있으며, 안전하지 않은 경로는 기본 경로로 바뀌는지 검증한다.
 */
@DataRedisTest
@Import(OAuthStateService.class)
class OAuthStateServiceTest {

    @Autowired
    private OAuthStateService oAuthStateService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("저장한 code_verifier와 돌아갈 경로를 함께 꺼낸다")
    void consumeReturnsCodeVerifierAndRedirectPath() {
        String state = UUID.randomUUID().toString();
        oAuthStateService.save(state, "verifier", "/admin/qr");

        assertThat(oAuthStateService.consume(state)).contains(new OAuthState("verifier", "/admin/qr"));
    }

    @Test
    @DisplayName("한 번 꺼낸 state는 다시 쓸 수 없다")
    void consumedStateCannotBeReused() {
        String state = UUID.randomUUID().toString();
        oAuthStateService.save(state, "verifier", "/admin/qr");
        oAuthStateService.consume(state);

        assertThat(oAuthStateService.consume(state)).isEmpty();
    }

    @Test
    @DisplayName("없는 state는 빈 값이다")
    void unknownStateIsEmpty() {
        assertThat(oAuthStateService.consume(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    @DisplayName("경로 없이 저장된 이전 형식은 로그인 완료 화면으로 돌아간다")
    void legacyValueWithoutPathUsesDefault() {
        String state = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("oauth:state:" + state, "verifier");

        assertThat(oAuthStateService.consume(state))
                .contains(new OAuthState("verifier", LoginRedirectPath.DEFAULT));
    }

    @Test
    @DisplayName("저장된 경로가 안전하지 않으면 로그인 완료 화면으로 바꾼다")
    void unsafeStoredPathUsesDefault() {
        String state = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("oauth:state:" + state, "verifier\n//evil.example");

        assertThat(oAuthStateService.consume(state))
                .contains(new OAuthState("verifier", LoginRedirectPath.DEFAULT));
    }
}
