package com.checkup.checkup.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.service.MemberService;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.support.MutableClock;

/**
 * 관리자 확인이 DB 역할로 관리자만 통과시키고, 역할을 짧게 기억해 DB를 매번 읽지 않으면서
 * 역할이 바뀌면 바로(또는 기억 시간이 지나면) 반영되는지 검증한다(#212).
 */
@ExtendWith(MockitoExtension.class)
class AdminVerifierTest {

    @Mock
    private MemberService memberService;

    private MutableClock clock;
    private AdminRoleCache cache;
    private AdminVerifier adminVerifier;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-07T00:00:00Z"));
        cache = new AdminRoleCache(clock);
        adminVerifier = new AdminVerifier(memberService, cache);
    }

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
    @DisplayName("없는 회원은 401이고 기억하지 않아 다음 요청도 DB를 본다")
    void unknownMemberIsUnauthorizedAndNotCached() {
        given(memberService.getById(3L))
                .willThrow(new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        assertThatThrownBy(() -> adminVerifier.verify(3L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
        assertThatThrownBy(() -> adminVerifier.verify(3L)).isInstanceOf(CustomException.class);

        verify(memberService, times(2)).getById(3L);
    }

    @Test
    @DisplayName("기억 시간 안의 두 번째 요청은 DB를 읽지 않는다")
    void secondRequestWithinTtlSkipsDatabase() {
        given(memberService.getById(1L)).willReturn(Member.create(100L, "관리자", MemberRole.ADMIN));

        adminVerifier.verify(1L);
        clock.advance(AdminRoleCache.TTL.minusSeconds(1));
        adminVerifier.verify(1L);

        verify(memberService, times(1)).getById(1L);
    }

    @Test
    @DisplayName("기억 시간이 지나면 DB에서 다시 읽어, 관리자에서 내려간 회원을 막는다")
    void demotedMemberIsRejectedAfterTtl() {
        given(memberService.getById(1L))
                .willReturn(Member.create(100L, "관리자", MemberRole.ADMIN))
                .willReturn(Member.create(100L, "관리자", MemberRole.STUDENT));

        adminVerifier.verify(1L);
        clock.advance(AdminRoleCache.TTL);

        assertThatThrownBy(() -> adminVerifier.verify(1L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADMIN_ONLY));
    }

    @Test
    @DisplayName("역할을 바꾸며 항목을 지우면 기억 시간 안에도 바로 새 역할로 판정한다")
    void evictionAppliesRoleChangeImmediately() {
        given(memberService.getById(1L))
                .willReturn(Member.create(100L, "관리자", MemberRole.ADMIN))
                .willReturn(Member.create(100L, "관리자", MemberRole.STUDENT));

        adminVerifier.verify(1L);
        cache.evict(1L);

        assertThatThrownBy(() -> adminVerifier.verify(1L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADMIN_ONLY));
    }
}
