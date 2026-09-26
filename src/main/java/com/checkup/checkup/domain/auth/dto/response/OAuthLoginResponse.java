package com.checkup.checkup.domain.auth.dto.response;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;

public record OAuthLoginResponse(
        String name,
        MemberRole role
) {
    public static OAuthLoginResponse from(Member member) {
        return new OAuthLoginResponse(member.getName(), member.getRole());
    }
}
