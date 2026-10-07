package com.checkup.checkup.domain.face.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import com.checkup.checkup.domain.face.config.FaceProperties;
import com.checkup.checkup.domain.face.service.FaceEnrollmentService;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

/**
 * 얼굴 등록 영상 요청이 본문을 받기 전에 걸러지고, 동시 처리 수만큼만 받으며 자리가 없으면 잠깐 기다린 뒤 429를 주는지 검증한다.
 */
class FacePayloadLimitFilterTest {

    private static final Long MEMBER_ID = 7L;
    private static final String ENROLLMENT_PATH = "/api/v1/face/enrollments";
    private static final String FRAME_PATH = "/api/v1/face/sessions/3fa85f64-5717-4562-b3fc-2c963f66afa6/frames";

    private final FaceEnrollmentService enrollmentService = mock(FaceEnrollmentService.class);
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("등록할 수 없는 학생의 요청은 본문을 받지 않고 바로 거절한다")
    void notEnrollableIsRejectedBeforeReadingBody() throws Exception {
        willThrow(new CustomException(ErrorCode.FACE_ALREADY_REGISTERED))
                .given(enrollmentService).verifyEnrollable(MEMBER_ID);
        FacePayloadLimitFilter filter = filter(1, Duration.ofSeconds(1));
        AtomicInteger handled = new AtomicInteger();
        MockHttpServletResponse response = new MockHttpServletResponse();

        login();
        filter.doFilter(request(ENROLLMENT_PATH), response, (req, res) -> handled.incrementAndGet());

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("FACE_ALREADY_REGISTERED");
        assertThat(handled).hasValue(0);
    }

    @Test
    @DisplayName("동시 처리 수만큼은 함께 받고, 넘는 요청은 기다린 뒤 429와 Retry-After를 받는다")
    void requestsBeyondLimitGet429WithRetryAfter() throws Exception {
        FacePayloadLimitFilter filter = filter(2, Duration.ofMillis(50));
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        FilterChain blocking = (req, res) -> {
            entered.countDown();
            await(release);
        };
        List<Future<MockHttpServletResponse>> running = List.of(
                submit(filter, ENROLLMENT_PATH, blocking), submit(filter, ENROLLMENT_PATH, blocking));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        MockHttpServletResponse rejected = submit(filter, ENROLLMENT_PATH, (req, res) -> { }).get(5, TimeUnit.SECONDS);

        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getContentAsString()).contains("FACE_SERVICE_BUSY");
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("1");
        release.countDown();
        for (Future<MockHttpServletResponse> future : running) {
            assertThat(future.get(5, TimeUnit.SECONDS).getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("기다리는 동안 자리가 나면 거절하지 않고 처리한다")
    void waitingRequestIsHandledWhenSlotFrees() throws Exception {
        FacePayloadLimitFilter filter = filter(1, Duration.ofSeconds(5));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<MockHttpServletResponse> first = submit(filter, ENROLLMENT_PATH, (req, res) -> {
            entered.countDown();
            await(release);
        });
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        AtomicInteger handled = new AtomicInteger();
        Future<MockHttpServletResponse> second =
                submit(filter, ENROLLMENT_PATH, (req, res) -> handled.incrementAndGet());

        release.countDown();

        assertThat(second.get(5, TimeUnit.SECONDS).getStatus()).isEqualTo(200);
        assertThat(first.get(5, TimeUnit.SECONDS).getStatus()).isEqualTo(200);
        assertThat(handled).hasValue(1);
    }

    @Test
    @DisplayName("프레임 요청은 자리가 없으면 기다리지 않고 Retry-After 없이 429를 받는다")
    void frameIsRejectedImmediatelyWithoutRetryAfter() throws Exception {
        FacePayloadLimitFilter filter = filter(1, Duration.ofSeconds(5));
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        FilterChain blocking = (req, res) -> {
            entered.countDown();
            await(release);
        };
        List<Future<MockHttpServletResponse>> running = List.of(
                submit(filter, FRAME_PATH, blocking), submit(filter, FRAME_PATH, blocking));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        MockHttpServletResponse rejected = submit(filter, FRAME_PATH, (req, res) -> { }).get(1, TimeUnit.SECONDS);

        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isNull();
        release.countDown();
        for (Future<MockHttpServletResponse> future : running) {
            future.get(5, TimeUnit.SECONDS);
        }
    }

    private FacePayloadLimitFilter filter(int maxConcurrentEnrollments, Duration enrollmentWaitTimeout) {
        FaceProperties properties = new FaceProperties(
                "http://face-ai.test", "secret", Duration.ofSeconds(2), Duration.ofSeconds(30),
                1024, 512, Duration.ofMillis(200), 2, maxConcurrentEnrollments, enrollmentWaitTimeout,
                Duration.ofMinutes(5), 60_000, "v1");
        FacePayloadLimitFilter filter =
                new FacePayloadLimitFilter(properties, JsonMapper.builder().build(), enrollmentService);
        filter.initialize();
        return filter;
    }

    private Future<MockHttpServletResponse> submit(FacePayloadLimitFilter filter, String path, FilterChain chain) {
        return executor.submit(() -> {
            login();
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request(path), response, chain);
            return response;
        });
    }

    private static void login() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(MEMBER_ID, null, List.of()));
    }

    private static MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        request.setContent(new byte[]{1, 2, 3});
        return request;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
