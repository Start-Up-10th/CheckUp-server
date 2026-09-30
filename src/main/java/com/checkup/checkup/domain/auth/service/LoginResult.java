package com.checkup.checkup.domain.auth.service;

import com.checkup.checkup.domain.member.entity.Member;

/**
 * 로그인을 마친 결과.
 *
 * @param member       저장·갱신된 회원
 * @param redirectPath 로그인 후 돌아갈 웹 경로
 */
public record LoginResult(
        Member member,
        String redirectPath
) {
}
