package com.checkup.checkup.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.checkup.checkup.domain.auth.dto.response.CurrentStudentResponse;
import com.checkup.checkup.domain.auth.dto.response.OAuthLoginResponse;
import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.member.service.MemberService;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;

/**
 * 현재 회원 조회가 세션 회원의 이름·역할·필수 동의 여부와 본인 학생 정보만 돌려주는지 검증한다.
 */
class CurrentMemberServiceTest {

    private static final Long MEMBER_ID = 7L;

    private final MemberService memberService = mock(MemberService.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final CurrentMemberService currentMemberService =
            new CurrentMemberService(memberService, studentRepository);

    @Test
    @DisplayName("학생은 세션 회원의 학생 정보(DataGSM 학생 id·학년·반·번호·학번·호실·층)를 받는다")
    void studentGetsOwnStudentInfo() {
        Member member = Member.create(100L, "학생", MemberRole.STUDENT);
        Student student = Student.create(member, 1234L, "학생", 2, 4, 5, 2405, 412);
        given(memberService.getById(MEMBER_ID)).willReturn(member);
        given(studentRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(student));

        OAuthLoginResponse response = currentMemberService.getCurrentMember(MEMBER_ID);

        assertThat(response.name()).isEqualTo("학생");
        assertThat(response.role()).isEqualTo(MemberRole.STUDENT);
        assertThat(response.consented()).isFalse();
        assertThat(response.student())
                .isEqualTo(new CurrentStudentResponse(1234L, 2, 4, 5, 2405, 412, 4));
    }

    @Test
    @DisplayName("필수 동의를 마친 학생은 consented가 true다")
    void consentedAfterRequiredConsent() {
        Member member = Member.create(100L, "학생", MemberRole.STUDENT);
        Student student = Student.create(member, 1234L, "학생", 2, 4, 5, 2405, 412);
        student.agree(false, Instant.parse("2026-10-03T00:00:00Z"), "v1");
        given(memberService.getById(MEMBER_ID)).willReturn(member);
        given(studentRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(student));

        assertThat(currentMemberService.getCurrentMember(MEMBER_ID).consented()).isTrue();
    }

    @Test
    @DisplayName("호실이 배정되지 않은 학생은 호실과 층이 null이다")
    void studentWithoutRoomHasNullRoomAndFloor() {
        Member member = Member.create(100L, "학생", MemberRole.STUDENT);
        Student student = Student.create(member, 1234L, "학생", 1, 1, 1, 1101, null);
        given(memberService.getById(MEMBER_ID)).willReturn(member);
        given(studentRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(student));

        CurrentStudentResponse info = currentMemberService.getCurrentMember(MEMBER_ID).student();

        assertThat(info.dormitoryRoom()).isNull();
        assertThat(info.dormitoryFloor()).isNull();
    }

    @Test
    @DisplayName("기숙사 자치위원은 ADMIN이면서 학생 정보가 있다")
    void dormitoryManagerIsAdminWithStudentInfo() {
        Member member = Member.create(100L, "자치위원", MemberRole.ADMIN);
        Student student = Student.create(member, 1234L, "자치위원", 3, 1, 2, 3102, 301);
        given(memberService.getById(MEMBER_ID)).willReturn(member);
        given(studentRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(student));

        OAuthLoginResponse response = currentMemberService.getCurrentMember(MEMBER_ID);

        assertThat(response.role()).isEqualTo(MemberRole.ADMIN);
        assertThat(response.student().studentNumber()).isEqualTo(3102);
    }

    @Test
    @DisplayName("학생 정보가 없는 회원(교사)은 student가 null이고 동의 완료가 아니다")
    void teacherHasNoStudentInfo() {
        given(memberService.getById(MEMBER_ID)).willReturn(Member.create(100L, "교사", MemberRole.ADMIN));
        given(studentRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());

        OAuthLoginResponse response = currentMemberService.getCurrentMember(MEMBER_ID);

        assertThat(response.student()).isNull();
        assertThat(response.consented()).isFalse();
    }

    @Test
    @DisplayName("회원이 없으면 MEMBER_NOT_FOUND이고 학생을 찾지 않는다")
    void missingMemberFails() {
        given(memberService.getById(MEMBER_ID)).willThrow(new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        assertThatThrownBy(() -> currentMemberService.getCurrentMember(MEMBER_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
        verify(studentRepository, never()).findByMemberId(MEMBER_ID);
    }
}
