package com.checkup.checkup.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

@DataRedisTest
@Import(OAuthStateService.class)
class OAuthStateServiceTest {

    @Autowired
    private OAuthStateService oAuthStateService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void 저장한_code_verifier와_돌아갈_경로를_함께_꺼낸다() {
        String state = UUID.randomUUID().toString();
        oAuthStateService.save(state, "verifier", "/admin/qr");

        assertThat(oAuthStateService.consume(state)).contains(new OAuthState("verifier", "/admin/qr"));
    }

    @Test
    void 한_번_꺼낸_state는_다시_쓸_수_없다() {
        String state = UUID.randomUUID().toString();
        oAuthStateService.save(state, "verifier", "/admin/qr");
        oAuthStateService.consume(state);

        assertThat(oAuthStateService.consume(state)).isEmpty();
    }

    @Test
    void 없는_state는_빈_값이다() {
        assertThat(oAuthStateService.consume(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    void 경로_없이_저장된_이전_형식은_로그인_완료_화면으로_돌아간다() {
        String state = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("oauth:state:" + state, "verifier");

        assertThat(oAuthStateService.consume(state))
                .contains(new OAuthState("verifier", LoginRedirectPath.DEFAULT));
    }

    @Test
    void 저장된_경로가_안전하지_않으면_로그인_완료_화면으로_바꾼다() {
        String state = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("oauth:state:" + state, "verifier\n//evil.example");

        assertThat(oAuthStateService.consume(state))
                .contains(new OAuthState("verifier", LoginRedirectPath.DEFAULT));
    }
}
