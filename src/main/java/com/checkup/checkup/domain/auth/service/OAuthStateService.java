package com.checkup.checkup.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * OAuth state와 PKCE code verifier를 Redis에 5분간 보관한다. state는 한 번만 사용할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class OAuthStateService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * state에 대응하는 code verifier를 저장한다.
     */
    public void save(String state, String codeVerifier) {
        String key = stateKey(state);
        stringRedisTemplate.opsForValue().set(key, codeVerifier, Duration.ofMinutes(5));
    }

    /**
     * state의 code verifier를 꺼내고 바로 삭제한다.
     *
     * @return code verifier. state가 없거나 만료되었거나 이미 사용되었으면 빈 값
     */
    public Optional<String> consume(String state) {
        String key = stateKey(state);
        String result = stringRedisTemplate.opsForValue().getAndDelete(key);
        return Optional.ofNullable(result);
    }

    private String stateKey(String state) {
        return "oauth:state:" + state;
    }

}
