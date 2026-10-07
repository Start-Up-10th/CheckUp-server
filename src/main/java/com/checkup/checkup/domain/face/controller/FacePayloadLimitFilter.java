package com.checkup.checkup.domain.face.controller;

import com.checkup.checkup.domain.face.config.FaceProperties;
import com.checkup.checkup.domain.face.service.FaceEnrollmentService;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ReadListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Spring MVC가 바이트 배열을 만들기 전에 얼굴 등록 영상·카메라 프레임 요청 본문의 크기와 동시 처리 수를 제한한다.
 *
 * - 얼굴 등록은 본문을 받기 전에 등록할 수 있는 학생인지 먼저 확인한다. 등록할 수 없는 요청이 영상을 올리는 동안 자리를 차지하지 않는다.
 * - 얼굴 등록은 자리가 없으면 잠깐 기다리고, 그래도 없으면 429와 {@code Retry-After}를 준다.
 * - 프레임은 다음 프레임이 곧 오므로 기다리지 않고 바로 429를 준다.
 */
@RequiredArgsConstructor
public class FacePayloadLimitFilter extends OncePerRequestFilter {
    private final FaceProperties properties;
    private final ObjectMapper objectMapper;
    private final FaceEnrollmentService faceEnrollmentService;
    private Semaphore framePermits;
    private Semaphore enrollmentPermits;

    @jakarta.annotation.PostConstruct
    void initialize() {
        framePermits = new Semaphore(properties.maxConcurrentFrames());
        // 얼굴 등록 영상 본문은 메모리에 올라가므로 동시에 받는 수를 설정값으로 묶는다. 먼저 기다린 요청부터 받는다.
        enrollmentPermits = new Semaphore(properties.maxConcurrentEnrollments(), true);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !"POST".equalsIgnoreCase(request.getMethod())
                || path == null
                || (!isEnrollmentPath(path) && !isFramePath(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean enrollment = isEnrollmentPath(request.getServletPath());
        long maxSize = enrollment ? properties.maxUploadBytes() : properties.maxFrameBytes();
        Semaphore permits = enrollment ? enrollmentPermits : framePermits;
        if (request.getContentLengthLong() > maxSize) {
            writeError(response, ErrorCode.FACE_UPLOAD_TOO_LARGE);
            return;
        }
        if (enrollment && !canEnroll(response)) {
            return;
        }
        if (!acquire(permits, enrollment)) {
            if (enrollment) {
                response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds()));
            }
            writeError(response, ErrorCode.FACE_SERVICE_BUSY);
            return;
        }
        try {
            byte[] body = readBounded(request, maxSize);
            if (body == null) {
                writeError(response, ErrorCode.FACE_UPLOAD_TOO_LARGE);
                return;
            }
            try {
                filterChain.doFilter(new CachedBodyRequest(request, body), response);
            } finally {
                Arrays.fill(body, (byte) 0);
            }
        } finally {
            permits.release();
        }
    }

    /**
     * 본문을 받기 전에 등록할 수 있는 학생인지 확인하고, 아니면 오류 응답을 쓴다. 같은 확인을 서비스가 저장 전에 다시 한다.
     */
    private boolean canEnroll(HttpServletResponse response) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long memberId)) {
            return true;
        }
        try {
            faceEnrollmentService.verifyEnrollable(memberId);
            return true;
        } catch (CustomException e) {
            writeError(response, e.getErrorCode());
            return false;
        }
    }

    private boolean acquire(Semaphore permits, boolean enrollment) {
        if (!enrollment) {
            return permits.tryAcquire();
        }
        try {
            return permits.tryAcquire(properties.enrollmentWaitTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private long retryAfterSeconds() {
        return Math.max(1, properties.enrollmentWaitTimeout().toSeconds());
    }

    private static boolean isEnrollmentPath(String path) {
        return "/api/v1/face/enrollments".equals(path);
    }

    private static boolean isFramePath(String path) {
        return path != null && path.startsWith("/api/v1/face/sessions/") && path.endsWith("/frames");
    }

    /** 입력이 설정한 최대 크기를 넘으면 null을 반환한다. 메모리에는 최대 크기+1바이트까지만 읽는다. */
    private static byte[] readBounded(HttpServletRequest request, long maxSize) throws IOException {
        int limit = (int) Math.min(maxSize, Integer.MAX_VALUE - 1L);
        SensitiveBuffer output = new SensitiveBuffer(Math.min(limit, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        jakarta.servlet.ServletInputStream input = request.getInputStream();
        try {
            int count;
            while (true) {
                int allowed = Math.min(buffer.length, limit + 1 - total);
                count = input.read(buffer, 0, allowed);
                if (count == -1) {
                    break;
                }
                total += count;
                if (total > limit) {
                    output.clearSensitive();
                    return null;
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            Arrays.fill(buffer, (byte) 0);
            output.clearSensitive();
        }
    }

    private static final class SensitiveBuffer extends ByteArrayOutputStream {
        private SensitiveBuffer(int size) {
            super(size);
        }

        private void clearSensitive() {
            Arrays.fill(buf, (byte) 0);
            reset();
        }
    }

    private void writeError(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getOutputStream().write(objectMapper.writeValueAsBytes(ErrorResponse.of(code)));
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new IllegalStateException("Async face-frame request bodies are not supported");
                }
            };
        }
    }
}
