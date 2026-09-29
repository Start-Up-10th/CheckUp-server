package com.checkup.checkup.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;

/**
 * 세션 기반 Spring Security 설정.
 *
 * <ul>
 *     <li>인증되지 않은 요청은 401, 권한이 없는 요청은 403을 반환한다.</li>
 *     <li>인증 실패 요청을 세션에 저장하지 않는다(RequestCache 끔). 로그인 후 복귀는 로그인 콜백이 직접 처리하므로
 *     필요 없고, 저장하면 로그인하지 않은 요청마다 빈 세션이 Redis에 생긴다.</li>
 *     <li>{@code POST /api/v1/auth/logout}은 세션을 무효화하고 {@code SESSION} 쿠키를 지운 뒤 204를 반환한다.</li>
 *     <li>{@code /api/v1/auth/me}를 제외한 {@code /api/v1/auth/**}는 로그인 없이 접근할 수 있다.</li>
 *     <li>{@code /api/v1/webhook}은 DataGSM이 로그인 없이 호출하며, 컨트롤러에서 서명으로 검증한다.</li>
 *     <li>API 문서({@code /v3/api-docs}, {@code /swagger-ui})는 로그인 없이 볼 수 있다. 끄려면 {@code SWAGGER_ENABLED=false}.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .logout(l -> l
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .logoutUrl("/api/v1/auth/logout")
                        .deleteCookies("SESSION")
                )
                .authorizeHttpRequests(
                        auth -> auth
                                .requestMatchers("/api/v1/auth/me").authenticated()
                                .requestMatchers("/api/v1/auth/**", "/error", "/api/v1/webhook").permitAll()
                                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                                .anyRequest().authenticated()
                );
        return http.build();
    }
}
