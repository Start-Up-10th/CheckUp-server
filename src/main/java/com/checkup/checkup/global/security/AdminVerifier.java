package com.checkup.checkup.global.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.service.MemberService;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 전용 API에서 현재 회원이 관리자인지 확인한다.
 *
 * <p>세션에 저장된 권한은 로그인 시점 값이라, 권한이 바뀌어도 바로 반영되도록 요청마다 DB의 역할을 본다.
 */
@Component
@RequiredArgsConstructor
public class AdminVerifier {

    private final MemberService memberService;

    /**
     * 관리자가 아니면 예외를 던진다.
     *
     * @param memberId 세션의 회원 id
     * @throws ResponseStatusException 회원이 없으면 401, 관리자가 아니면 403
     */
    public void verify(Long memberId) {
        if (memberService.getById(memberId).getRole() != MemberRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 사용할 수 있습니다.");
        }
    }
}
