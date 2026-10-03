package com.checkup.checkup.domain.auth.dto.response;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;

/**
 * 로그인·현재 회원 조회 응답.
 *
 * @param name      회원 이름
 * @param role      회원 역할
 * @param consented 필수 동의(개인정보, 얼굴 정보)를 마쳤는지. 학생이 아닌 회원은 false다.
 * @param student   학생 정보. 학생 정보가 없는 회원(교사)은 null이다. 기숙사 자치위원은 ADMIN이면서 학생 정보가 있다.
 */
public record OAuthLoginResponse(
        String name,
        MemberRole role,
        boolean consented,
        CurrentStudentResponse student
) {
    public static OAuthLoginResponse from(Member member, boolean consented, CurrentStudentResponse student) {
        return new OAuthLoginResponse(member.getName(), member.getRole(), consented, student);
    }
}
