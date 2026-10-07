package com.checkup.checkup.global.querylog;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 요청마다 응답 상태, DB 쿼리 수, 걸린 시간을 로그 한 줄로 남긴다.
 *
 * - 형식: {@code GET /api/v1/room/floor status=200 queries=2 elapsedMs=14}
 * - 경로만 남기고 쿼리 문자열, 헤더, 본문은 남기지 않는다. 로그인 콜백의 code·state가 쿼리 문자열에 있기 때문이다.
 * - {@code /actuator} 아래는 남기지 않는다. 상태 확인이 10초마다 들어와 다른 줄을 가린다.
 */
@Slf4j
@RequiredArgsConstructor
public class QueryCountFilter extends OncePerRequestFilter {

    private static final String ACTUATOR_PATH = "/actuator";

    private final QueryCounter queryCounter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals(ACTUATOR_PATH) || path.startsWith(ACTUATOR_PATH + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        queryCounter.start();
        try {
            filterChain.doFilter(request, response);
        } finally {
            int queries = queryCounter.stop();
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("{} {} status={} queries={} elapsedMs={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), queries, elapsedMs);
        }
    }
}
