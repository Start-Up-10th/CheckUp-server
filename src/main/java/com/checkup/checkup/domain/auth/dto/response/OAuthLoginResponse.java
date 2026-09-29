package com.checkup.checkup.domain.auth.dto.response;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;

/**
 * 로그인·현재 회원 조회 응답.
 *
 * @param name 회원 이름
 * @param role 회원 역할
 */
public record OAuthLoginResponse(
        String name,
        MemberRole role
) {
    public static OAuthLoginResponse from(Member member) {
        return new OAuthLoginResponse(member.getName(), member.getRole());
    }
}
