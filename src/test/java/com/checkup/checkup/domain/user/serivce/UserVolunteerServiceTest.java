package com.checkup.checkup.domain.user.serivce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.member.service.MemberService;
import com.checkup.checkup.domain.user.dto.Response.UserVolunteerResponse;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

/**
 * 학생 봉사 횟수를 관리자와 본인만 조회할 수 있고, 없는 학생은 404인지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class UserVolunteerServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long STUDENT_ID = 100L;

    @Mock
    private MemberService memberService;

    @Mock
    private StudentRepository studentRepository;

    private UserVolunteerService service;

    @BeforeEach
    void setUp() {
        service = new UserVolunteerService(memberService, studentRepository);
    }

    @Test
    @DisplayName("관리자는 다른 학생의 봉사 횟수를 조회한다")
    void adminCanRead() {
        givenMember(MemberRole.ADMIN);
        Member other = Member.create(20L, "김학생", MemberRole.STUDENT);
        given(studentRepository.findByDatagsmStudentId(STUDENT_ID))
                .willReturn(Optional.of(student(other, STUDENT_ID)));

        UserVolunteerResponse response = service.findVolunteer(MEMBER_ID, STUDENT_ID);

        assertThat(response.studentId()).isEqualTo(STUDENT_ID);
        assertThat(response.volunteerCount()).isZero();
    }

    @Test
    @DisplayName("학생은 본인의 봉사 횟수를 조회한다")
    void studentCanReadSelf() {
        Member member = givenMember(MemberRole.STUDENT);
        Student own = student(member, STUDENT_ID);
        given(studentRepository.findByMember(member)).willReturn(Optional.of(own));
        given(studentRepository.findByDatagsmStudentId(STUDENT_ID)).willReturn(Optional.of(own));

        assertThat(service.findVolunteer(MEMBER_ID, STUDENT_ID).volunteerCount()).isZero();
    }

    @Test
    @DisplayName("학생이 다른 학생의 봉사 횟수를 조회하면 403이다")
    void studentCannotReadOthers() {
        Member member = givenMember(MemberRole.STUDENT);
        given(studentRepository.findByMember(member)).willReturn(Optional.of(student(member, 999L)));

        assertThatThrownBy(() -> service.findVolunteer(MEMBER_ID, STUDENT_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(studentRepository, never()).findByDatagsmStudentId(STUDENT_ID);
    }

    @Test
    @DisplayName("저장된 학생이 없으면 404이다")
    void notFound() {
        givenMember(MemberRole.ADMIN);
        given(studentRepository.findByDatagsmStudentId(STUDENT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findVolunteer(MEMBER_ID, STUDENT_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STUDENT_NOT_FOUND));
    }

    private Member givenMember(MemberRole role) {
        Member member = Member.create(10L, "홍길동", role);
        given(memberService.getById(MEMBER_ID)).willReturn(member);
        return member;
    }

    private Student student(Member member, Long datagsmStudentId) {
        return Student.create(member, datagsmStudentId, "홍길동", 2, 3, 4, 2304, 301);
    }
}
