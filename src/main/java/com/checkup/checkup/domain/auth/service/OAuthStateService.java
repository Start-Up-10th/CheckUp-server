package com.checkup.checkup.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OAuthStateService {

    private final StringRedisTemplate stringRedisTemplate;

    public void save(String state, String codeVerifier) {
        String key = stateKey(state);
        stringRedisTemplate.opsForValue().set(key, codeVerifier, Duration.ofMinutes(5));
    }

    public Optional<String> consume(String state) {
        String key = stateKey(state);
        String result = stringRedisTemplate.opsForValue().getAndDelete(key);
        return Optional.ofNullable(result);
    }

    private String stateKey(String state) {
        return "oauth:state:" + state;
    }

}
