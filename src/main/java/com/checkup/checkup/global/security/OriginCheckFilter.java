package com.checkup.checkup.global.security;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

import org.springframework.web.filter.OncePerRequestFilter;

import com.checkup.checkup.global.exception.ErrorCode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 상태를 바꾸는 요청이 웹 주소({@code PUBLIC_ORIGIN})에서 왔는지 확인해 CSRF를 막는다(#146).
 *
 * - 세션 쿠키의 {@code SameSite=Lax}는 같은 등록 도메인의 다른 서브도메인·포트 요청을 막지 못해서 서버가 출처를 직접 확인한다.
 * - GET·HEAD·OPTIONS·TRACE는 상태를 바꾸지 않으므로 확인하지 않는다.
 * - {@code /api/v1/webhook}은 DataGSM 서버가 호출하고 서명으로 검증하므로 확인하지 않는다.
 * - {@code Origin}이 있으면 그것을, 없으면 {@code Referer}의 origin을 비교한다. 다르거나 해석할 수 없으면 403이다.
 * - 둘 다 없으면 통과시킨다. 브라우저는 상태를 바꾸는 요청에 {@code Origin}을 붙이므로 둘 다 없는 요청은 브라우저 밖 요청이다.
 * - 헤더 값은 응답과 로그에 넣지 않는다.
 */
public class OriginCheckFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final String WEBHOOK_PATH = "/api/v1/webhook";

    private final String allowedOrigin;
    private final SecurityErrorHandler errorHandler;

    /**
     * @param baseUrl      웹 주소. {@code WebProperties}에서 이미 origin 형식으로 검증된 값이다.
     * @param errorHandler 403 응답을 다른 보안 오류와 같은 형식으로 쓴다.
     */
    public OriginCheckFilter(String baseUrl, SecurityErrorHandler errorHandler) {
        this.allowedOrigin = toOrigin(baseUrl);
        if (this.allowedOrigin == null) {
            throw new IllegalArgumentException("PUBLIC_ORIGIN must be an absolute http(s) origin");
        }
        this.errorHandler = errorHandler;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return SAFE_METHODS.contains(request.getMethod())
                || path.equals(WEBHOOK_PATH)
                || path.startsWith(WEBHOOK_PATH + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String source = request.getHeader("Origin");
        if (source == null) {
            source = request.getHeader("Referer");
        }

        if (source != null && !allowedOrigin.equals(toOrigin(source))) {
            errorHandler.write(response, ErrorCode.INVALID_ORIGIN);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 주소에서 {@code scheme://host[:port]}만 소문자로 뽑는다. 기본 포트(http 80, https 443)는 뺀다.
     * {@code Origin: null}처럼 http(s) 주소가 아니거나 해석할 수 없으면 {@code null}이다.
     */
    private static String toOrigin(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) {
            return null;
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return null;
        }

        int port = uri.getPort();
        boolean defaultPort = port == -1
                || (scheme.equals("http") && port == 80)
                || (scheme.equals("https") && port == 443);
        String origin = scheme + "://" + host.toLowerCase(Locale.ROOT);
        return defaultPort ? origin : origin + ":" + port;
    }
}
