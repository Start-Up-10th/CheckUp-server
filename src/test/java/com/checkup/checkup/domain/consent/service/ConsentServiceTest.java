package com.checkup.checkup.domain.consent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;

class ConsentServiceTest {

    private static final Long MEMBER_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-09-30T00:00:00Z");

    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final ConsentService consentService =
            new ConsentService(studentRepository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("로그인한 학생의 필수 동의 시각과 공지 알림 수신 여부를 기록한다")
    void agreeRecordsConsentOfLoggedInStudent() {
        Student student = student();
        given(studentRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(student));

        consentService.agree(MEMBER_ID, true);

        assertThat(student.getPrivacyAgreedAt()).isEqualTo(NOW);
        assertThat(student.getFaceAgreedAt()).isEqualTo(NOW);
        assertThat(student.isNoticeAlarmAgreed()).isTrue();
    }

    @Test
    @DisplayName("학생이 아닌 회원은 동의할 수 없다")
    void nonStudentCannotAgree() {
        given(studentRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> consentService.agree(MEMBER_ID, false))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MISSING_STUDENT_INFO));
    }

    @Test
    @DisplayName("필수 동의를 마친 학생만 동의 완료다")
    void hasRequiredConsentOnlyAfterAgree() {
        Student student = student();
        given(studentRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(student));

        assertThat(consentService.hasRequiredConsent(MEMBER_ID)).isFalse();

        student.agree(false, NOW);

        assertThat(consentService.hasRequiredConsent(MEMBER_ID)).isTrue();
    }

    @Test
    @DisplayName("학생이 아닌 회원은 동의 완료가 아니다")
    void nonStudentHasNoConsent() {
        given(studentRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());

        assertThat(consentService.hasRequiredConsent(MEMBER_ID)).isFalse();
    }

    private static Student student() {
        return Student.create(Member.create(1L, "학생", MemberRole.STUDENT), 1L, 1, 1, 1, 1101, 301);
    }
}
