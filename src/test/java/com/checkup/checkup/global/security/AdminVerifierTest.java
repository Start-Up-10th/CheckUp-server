package com.checkup.checkup.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

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

@ExtendWith(MockitoExtension.class)
class AdminVerifierTest {

    @Mock
    private MemberService memberService;

    @InjectMocks
    private AdminVerifier adminVerifier;

    @Test
    void 관리자는_통과한다() {
        given(memberService.getById(1L)).willReturn(Member.create(100L, "관리자", MemberRole.ADMIN));

        assertThatCode(() -> adminVerifier.verify(1L)).doesNotThrowAnyException();
    }

    @Test
    void 학생은_403이다() {
        given(memberService.getById(2L)).willReturn(Member.create(200L, "학생", MemberRole.STUDENT));

        assertThatThrownBy(() -> adminVerifier.verify(2L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADMIN_ONLY));
    }

    @Test
    void 없는_회원은_401이다() {
        given(memberService.getById(3L))
                .willThrow(new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        assertThatThrownBy(() -> adminVerifier.verify(3L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
    }
}
