package com.checkup.checkup.global.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutFilter;

import com.checkup.checkup.global.config.WebProperties;

import tools.jackson.databind.ObjectMapper;

/**
 * 세션 기반 Spring Security 설정.
 *
 * - 인증되지 않은 요청은 401, 권한이 없는 요청은 403을 다른 API 오류와 같은 {@code code}·{@code message} 형식으로 반환한다.
 * - 인증 실패 요청을 세션에 저장하지 않는다(RequestCache 끔). 로그인 후 복귀는 로그인 콜백이 직접 처리하므로
 *   필요 없고, 저장하면 로그인하지 않은 요청마다 빈 세션이 Redis에 생긴다.
 * - {@code POST /api/v1/auth/logout}은 세션을 무효화하고 {@code SESSION} 쿠키를 지운 뒤 204를 반환한다.
 * - {@code /api/v1/auth/me}를 제외한 {@code /api/v1/auth/**}는 로그인 없이 접근할 수 있다.
 * - {@code /api/v1/webhook}은 DataGSM이 로그인 없이 호출하며, 컨트롤러에서 서명으로 검증한다.
 * - API 문서({@code /v3/api-docs}, {@code /swagger-ui})는 로그인 없이 볼 수 있다. 끄려면 {@code SWAGGER_ENABLED=false}.
 * - 상태 확인({@code /actuator/health})은 배포·컨테이너 점검이 로그인 없이 호출한다(REQ-OPS-001). 다른 actuator 경로는 로그인이 필요하다.
 * - 지표({@code /actuator/prometheus})는 {@code checkup.metrics.enabled}가 true일 때만 로그인 없이 연다. Prometheus는 세션 로그인을 할 수 없다.
 *   기본은 꺼져 있고 운영 compose는 이 값을 넘기지 않는다.
 * - CSRF 토큰 대신 {@link OriginCheckFilter}로 상태 변경 요청의 출처를 확인한다. 로그아웃도 확인하도록 {@link LogoutFilter} 앞에 둔다.
 */
@Configuration
@EnableConfigurationProperties(WebProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
                                                   WebProperties webProperties,
                                                   @Value("${checkup.metrics.enabled:false}") boolean metricsEnabled)
            throws Exception {
        SecurityErrorHandler errorHandler = new SecurityErrorHandler(objectMapper);
        http
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler)
                )
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(new OriginCheckFilter(webProperties.baseUrl(), errorHandler), LogoutFilter.class)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .logout(l -> l
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .logoutUrl("/api/v1/auth/logout")
                        .deleteCookies("SESSION")
                )
                .authorizeHttpRequests(
                        auth -> {
                            if (metricsEnabled) {
                                auth.requestMatchers("/actuator/prometheus").permitAll();
                            }
                            auth
                                    .requestMatchers("/api/v1/auth/me").authenticated()
                                    .requestMatchers("/api/v1/auth/**", "/error", "/api/v1/webhook").permitAll()
                                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                                    .permitAll()
                                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                                    .anyRequest().authenticated();
                        }
                );
        return http.build();
    }
}
