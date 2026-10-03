package com.checkup.checkup.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.service.MemberService;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;

/**
 * 관리자 확인이 요청마다 DB 역할로 관리자만 통과시키는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AdminVerifierTest {

    @Mock
    private MemberService memberService;

    @InjectMocks
    private AdminVerifier adminVerifier;

    @Test
    @DisplayName("관리자는 통과한다")
    void adminPasses() {
        given(memberService.getById(1L)).willReturn(Member.create(100L, "관리자", MemberRole.ADMIN));

        assertThatCode(() -> adminVerifier.verify(1L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("학생은 403이다")
    void studentIsForbidden() {
        given(memberService.getById(2L)).willReturn(Member.create(200L, "학생", MemberRole.STUDENT));

        assertThatThrownBy(() -> adminVerifier.verify(2L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADMIN_ONLY));
    }

    @Test
    @DisplayName("없는 회원은 401이다")
    void unknownMemberIsUnauthorized() {
        given(memberService.getById(3L))
                .willThrow(new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        assertThatThrownBy(() -> adminVerifier.verify(3L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
    }
}
