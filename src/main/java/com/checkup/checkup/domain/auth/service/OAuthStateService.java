package com.checkup.checkup.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * OAuth state에 PKCE code verifier와 로그인 후 돌아갈 경로를 묶어 Redis에 5분간 보관한다.
 * state는 한 번만 사용할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class OAuthStateService {

    private static final String SEPARATOR = "\n";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * state에 대응하는 code verifier와 돌아갈 경로를 저장한다.
     *
     * @param redirectPath {@link LoginRedirectPath#sanitize}를 거친 경로. 줄바꿈을 포함하지 않는다.
     */
    public void save(String state, String codeVerifier, String redirectPath) {
        String key = stateKey(state);
        stringRedisTemplate.opsForValue().set(key, codeVerifier + SEPARATOR + redirectPath, Duration.ofMinutes(5));
    }

    /**
     * state에 저장한 값을 꺼내고 바로 삭제한다. 꺼내기와 삭제는 Redis에서 한 번에 처리된다.
     *
     * @return 저장한 값. state가 없거나 만료되었거나 이미 사용되었으면 빈 값
     */
    public Optional<OAuthState> consume(String state) {
        String key = stateKey(state);
        String result = stringRedisTemplate.opsForValue().getAndDelete(key);
        if (result == null) {
            return Optional.empty();
        }
        int separator = result.indexOf(SEPARATOR);
        if (separator < 0) {
            return Optional.of(new OAuthState(result, LoginRedirectPath.DEFAULT));
        }
        return Optional.of(new OAuthState(
                result.substring(0, separator),
                LoginRedirectPath.sanitize(result.substring(separator + 1))
        ));
    }

    private String stateKey(String state) {
        return "oauth:state:" + state;
    }

}
