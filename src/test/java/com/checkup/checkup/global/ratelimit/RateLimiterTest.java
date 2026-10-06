package com.checkup.checkup.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;

class RateLimiterTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final RateLimiter rateLimiter = new RateLimiter(redisTemplate);

    private void givenCount(long count) {
        given(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))).willReturn(count);
    }

    @Test
    @DisplayName("한도 이하의 호출은 통과한다")
    void allowsUpToLimit() {
        givenCount(30);

        assertThatCode(() -> rateLimiter.check("user-search", 7L, 30, Duration.ofMinutes(1)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("한도를 넘으면 429 TOO_MANY_REQUESTS다")
    void rejectsOverLimit() {
        givenCount(31);

        assertThatThrownBy(() -> rateLimiter.check("user-search", 7L, 30, Duration.ofMinutes(1)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS));
    }

    @Test
    @DisplayName("키는 제한 이름과 회원 id로 나누고 창 길이를 초로 넘긴다")
    void keyIsPerNameAndMember() {
        givenCount(1);

        rateLimiter.check("qr-scan", 7L, 20, Duration.ofMinutes(1));

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("ratelimit:qr-scan:7")), eq("60"));
    }

    @Test
    @DisplayName("Redis 오류면 요청을 막지 않고 통과시킨다")
    void failsOpenWhenRedisIsDown() {
        given(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .willThrow(new IllegalStateException("redis down"));

        assertThatCode(() -> rateLimiter.check("qr-scan", 7L, 20, Duration.ofMinutes(1)))
                .doesNotThrowAnyException();
    }
}
