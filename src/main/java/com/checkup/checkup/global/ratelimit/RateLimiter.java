package com.checkup.checkup.global.ratelimit;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원별 API 호출 횟수를 Redis 고정 창으로 제한한다. 서버가 여러 대여도 같은 횟수를 공유한다.
 * Redis 장애로 확인하지 못하면 요청을 막지 않고 통과시킨다(제한이 서비스 장애가 되지 않게 한다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiter {

    /** 증가와 만료 설정을 한 번에 실행해, 만료 없는 키가 남아 영구 차단되는 일을 막는다. */
    private static final DefaultRedisScript<Long> INCREMENT_WITH_EXPIRE = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]) "
                    + "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return count",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * @param name     제한 대상 이름(API별로 다르게 준다)
     * @param memberId 호출한 회원 id
     * @param limit    창 안에서 허용하는 호출 수
     * @param window   창 길이
     * @throws CustomException 한도를 넘으면 {@link ErrorCode#TOO_MANY_REQUESTS}(429)
     */
    public void check(String name, Long memberId, int limit, Duration window) {
        Long count;
        try {
            count = redisTemplate.execute(INCREMENT_WITH_EXPIRE,
                    List.of("ratelimit:" + name + ":" + memberId), String.valueOf(window.toSeconds()));
        } catch (RuntimeException e) {
            log.warn("Rate limit check skipped: name={}, cause={}", name, e.getClass().getSimpleName());
            return;
        }
        if (count != null && count > limit) {
            throw new CustomException(ErrorCode.TOO_MANY_REQUESTS);
        }
    }
}
