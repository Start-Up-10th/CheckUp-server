package com.checkup.checkup.domain.auth.controller;

import com.checkup.checkup.domain.auth.dto.request.OAuthCallbackRequest;
import com.checkup.checkup.domain.auth.dto.response.OAuthLoginResponse;
import com.checkup.checkup.domain.auth.service.AuthService;
import com.checkup.checkup.domain.auth.service.LoginSessionService;
import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * DataGSM OAuth 로그인과 세션 사용자 조회 API.
 *
 * <p>로그아웃({@code POST /api/v1/auth/logout})은 컨트롤러가 아니라
 * {@link com.checkup.checkup.global.security.SecurityConfig}의 Spring Security 로그아웃 필터가 처리한다.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginSessionService loginSessionService;
    private final MemberService memberService;

    /**
     * DataGSM 로그인 페이지로 보낸다.
     *
     * @return state·PKCE가 포함된 DataGSM 인가 URL로의 302 응답
     */
    @GetMapping("/login")
    public ResponseEntity<Void> login() {
        String url = authService.createLoginUrl();
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    /**
     * DataGSM 인가 후 돌아오는 콜백. 토큰을 교환해 회원을 저장하고 로그인 세션을 만든다.
     *
     * @param request DataGSM이 넘겨준 code와 state
     * @return 로그인한 회원의 이름과 역할
     */
    @GetMapping("/callback")
    public OAuthLoginResponse callback(@Valid OAuthCallbackRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Member member = authService.completeLogin(request.code(), request.state());
        loginSessionService.login(member, httpRequest, httpResponse);
        return OAuthLoginResponse.from(member);
    }

    /**
     * 세션 쿠키로 현재 로그인한 회원을 조회한다. 로그인하지 않았으면 401을 반환한다.
     *
     * @param memberId 세션에 저장된 회원 id
     * @return 현재 회원의 이름과 역할
     */
    @GetMapping("/me")
    public OAuthLoginResponse me(@AuthenticationPrincipal Long memberId) {
        Member member = memberService.getById(memberId);
        return OAuthLoginResponse.from(member);
    }

}
