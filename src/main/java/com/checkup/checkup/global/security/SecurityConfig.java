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
 *     <li>{@code POST /auth/logout}은 세션을 무효화하고 {@code SESSION} 쿠키를 지운 뒤 204를 반환한다.</li>
 *     <li>{@code /auth/me}를 제외한 {@code /auth/**}는 로그인 없이 접근할 수 있다.</li>
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
                .logout(l -> l
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .logoutUrl("/auth/logout")
                        .deleteCookies("SESSION")
                )
                .authorizeHttpRequests(
                        auth -> auth
                                .requestMatchers("/auth/me").authenticated()
                                .requestMatchers("/auth/**", "/error").permitAll()
                                .anyRequest().authenticated()
                );
        return http.build();
    }
}
