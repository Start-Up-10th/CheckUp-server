package com.checkup.checkup.domain.auth.controller;

import com.checkup.checkup.domain.auth.dto.request.OAuthCallbackRequest;
import com.checkup.checkup.domain.auth.dto.response.OAuthLoginResponse;
import com.checkup.checkup.domain.auth.service.AuthService;
import com.checkup.checkup.domain.auth.service.LoginSessionService;
import com.checkup.checkup.domain.member.entity.Member;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginSessionService loginSessionService;

    @GetMapping("/login")
    public ResponseEntity<Void> login() {
        String url = authService.createLoginUrl();
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    @GetMapping("/callback")
    public OAuthLoginResponse callback(@Valid OAuthCallbackRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Member member = authService.completeLogin(request.code(), request.state());
        loginSessionService.login(member, httpRequest, httpResponse);
        return OAuthLoginResponse.from(member);
    }
}
