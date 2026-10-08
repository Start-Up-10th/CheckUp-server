package com.checkup.checkup.global.security;

import org.springframework.stereotype.Component;

import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.service.MemberService;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 전용 API에서 현재 회원이 관리자인지 확인한다.
 *
 * 세션에 저장된 권한은 로그인 시점 값이라, 권한이 바뀌어도 바로 반영되도록 세션이 아닌 DB의 역할을 본다.
 * 요청마다 DB를 읽지 않도록 역할을 {@link AdminRoleCache}에 짧게 두고, 역할을 바꾸는 로그인·동기화가 커밋 뒤에 그 항목을 지운다(#212).
 */
@Component
@RequiredArgsConstructor
public class AdminVerifier {

    private final MemberService memberService;
    private final AdminRoleCache adminRoleCache;

    /**
     * 관리자가 아니면 예외를 던진다.
     *
     * @param memberId 세션의 회원 id
     * @throws CustomException 회원이 없으면 {@link ErrorCode#MEMBER_NOT_FOUND}(401),
     *                         관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403)
     */
    public void verify(Long memberId) {
        MemberRole role = adminRoleCache.get(memberId).orElseGet(() -> load(memberId));
        if (role != MemberRole.ADMIN) {
            throw new CustomException(ErrorCode.ADMIN_ONLY);
        }
    }

    private MemberRole load(Long memberId) {
        MemberRole role = memberService.getById(memberId).getRole();
        adminRoleCache.put(memberId, role);
        return role;
    }
}
