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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.service.MemberService;

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
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void 없는_회원은_401이다() {
        given(memberService.getById(3L))
                .willThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "존재하지 않는 회원입니다."));

        assertThatThrownBy(() -> adminVerifier.verify(3L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
