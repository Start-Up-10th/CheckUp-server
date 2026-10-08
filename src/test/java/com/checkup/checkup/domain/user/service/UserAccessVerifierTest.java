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

import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.security.AdminVerifier;

/**
 * 학생 id 기준 조회의 접근 권한(관리자·본인)을 검증한다.
 */
class UserAccessVerifierTest {

    private static final Long MEMBER_ID = 2L;
    private static final Long STUDENT_ID = 100L;

    private final AdminVerifier adminVerifier = mock(AdminVerifier.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final UserAccessVerifier verifier = new UserAccessVerifier(adminVerifier, studentRepository);

    @Test
    @DisplayName("관리자는 학생 저장소를 보지 않고 통과한다")
    void adminPasses() {
        given(adminVerifier.isAdmin(1L)).willReturn(true);

        verifier.verify(1L, STUDENT_ID);

        verifyNoInteractions(studentRepository);
    }

    @Test
    @DisplayName("본인 학생 id면 통과한다")
    void selfPasses() {
        given(studentRepository.findDatagsmStudentIdByMemberId(MEMBER_ID)).willReturn(Optional.of(STUDENT_ID));

        assertThatCode(() -> verifier.verify(MEMBER_ID, STUDENT_ID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("다른 학생 id면 FORBIDDEN이다")
    void otherStudentIsForbidden() {
        given(studentRepository.findDatagsmStudentIdByMemberId(MEMBER_ID)).willReturn(Optional.of(999L));

        assertForbidden();
    }

    @Test
    @DisplayName("학생 정보가 없는 회원은 FORBIDDEN이다")
    void memberWithoutStudentIsForbidden() {
        given(studentRepository.findDatagsmStudentIdByMemberId(MEMBER_ID)).willReturn(Optional.empty());

        assertForbidden();
    }

    @Test
    @DisplayName("없는 회원은 401이고 학생 저장소를 보지 않는다")
    void unknownMemberIsUnauthorized() {
        given(adminVerifier.isAdmin(MEMBER_ID)).willThrow(new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        assertThatThrownBy(() -> verifier.verify(MEMBER_ID, STUDENT_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
        verifyNoInteractions(studentRepository);
    }

    private void assertForbidden() {
        assertThatThrownBy(() -> verifier.verify(MEMBER_ID, STUDENT_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }
}
