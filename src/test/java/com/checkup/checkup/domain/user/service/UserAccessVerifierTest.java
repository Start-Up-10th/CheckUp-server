package com.checkup.checkup.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;

/**
 * 학생 id 기준 조회의 접근 권한(관리자·본인)을 검증한다.
 */
class UserAccessVerifierTest {

    private static final Long STUDENT_ID = 100L;

    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final UserAccessVerifier verifier = new UserAccessVerifier(studentRepository);

    @Test
    @DisplayName("관리자는 학생 저장소를 보지 않고 통과한다")
    void adminPasses() {
        verifier.verify(Member.create(1L, "관리자", MemberRole.ADMIN), STUDENT_ID);

        verifyNoInteractions(studentRepository);
    }

    @Test
    @DisplayName("본인 학생 id면 통과한다")
    void selfPasses() {
        Member member = Member.create(2L, "학생", MemberRole.STUDENT);
        givenStudentOf(member, STUDENT_ID);

        assertThatCode(() -> verifier.verify(member, STUDENT_ID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("다른 학생 id면 FORBIDDEN이다")
    void otherStudentIsForbidden() {
        Member member = Member.create(2L, "학생", MemberRole.STUDENT);
        givenStudentOf(member, 999L);

        assertForbidden(member);
    }

    @Test
    @DisplayName("학생 정보가 없는 회원은 FORBIDDEN이다")
    void memberWithoutStudentIsForbidden() {
        Member member = Member.create(2L, "학생", MemberRole.STUDENT);
        given(studentRepository.findByMember(member)).willReturn(Optional.empty());

        assertForbidden(member);
    }

    private void assertForbidden(Member member) {
        assertThatThrownBy(() -> verifier.verify(member, STUDENT_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    private void givenStudentOf(Member member, Long datagsmStudentId) {
        Student student = Student.create(member, datagsmStudentId, "홍길동", 2, 3, 4, 2304, 301);
        given(studentRepository.findByMember(member)).willReturn(Optional.of(student));
    }
}
