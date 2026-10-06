package com.checkup.checkup.global.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.exception.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * Security 필터에서 막힌 요청을 다른 API 오류와 같은 {@link ErrorResponse} 형식으로 응답한다.
 *
 * 로그인하지 않은 요청은 401 {@code UNAUTHORIZED}, 권한이 없는 요청은 403 {@code FORBIDDEN}이다.
 * 필터에서 막혀 컨트롤러에 도달하지 않으므로 {@code GlobalExceptionHandler}가 처리하지 못한다.
 * 요청 정보·세션 값·예외 메시지는 응답과 로그에 넣지 않는다.
 */
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, ErrorCode.UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, ErrorCode.FORBIDDEN);
    }

    /**
     * 같은 패키지의 보안 필터도 같은 형식으로 오류를 쓰도록 package-private으로 연다.
     */
    void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(errorCode));
    }
}
