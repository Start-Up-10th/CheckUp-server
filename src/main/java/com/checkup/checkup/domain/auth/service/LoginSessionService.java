package com.checkup.checkup.domain.auth.service;

import com.checkup.checkup.domain.member.entity.Member;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 로그인한 회원의 인증 정보를 Redis 세션에 저장한다.
 */
@Service
public class LoginSessionService {
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    /**
     * 기존 세션을 무효화하고 새 세션에 회원 id와 역할을 저장한다.
     * 세션을 새로 발급해 세션 고정 공격을 막는다.
     *
     * @param member 로그인한 회원
     */
    public void login(Member member, HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                member.getId(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        securityContextRepository.saveContext(context, request, response);
    }
}
