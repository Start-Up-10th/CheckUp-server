package com.checkup.checkup.domain.auth.dto.response;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 로그인·현재 회원 조회 응답.
 *
 * @param name      회원 이름
 * @param role      회원 역할
 * @param consented 필수 동의(개인정보, 얼굴 정보)를 마쳤는지. 학생이 아닌 회원은 false다.
 * @param student   학생 정보. 학생 정보가 없는 회원(교사)은 null이다. 기숙사 자치위원은 ADMIN이면서 학생 정보가 있다.
 */
public record OAuthLoginResponse(
        @Schema(description = "회원 이름", example = "홍길동") String name,
        @Schema(description = "회원 역할. STUDENT 또는 ADMIN(사감·기숙사 자치위원·관리자 허용 목록)", example = "STUDENT") MemberRole role,
        @Schema(description = "필수 동의(개인정보, 얼굴 정보)를 마쳤는지. 학생이 아닌 회원은 false") boolean consented,
        @Schema(description = "학생 정보. 학생 정보가 없는 회원(교사)은 null. 기숙사 자치위원은 ADMIN이면서 학생 정보가 있다") CurrentStudentResponse student
) {
    public static OAuthLoginResponse from(Member member, boolean consented, CurrentStudentResponse student) {
        return new OAuthLoginResponse(member.getName(), member.getRole(), consented, student);
    }
}
