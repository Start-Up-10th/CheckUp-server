package com.checkup.checkup.domain.qr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.checkup.checkup.domain.attendance.entity.AttendanceMethod;
import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.attendance.entity.AttendanceRecordResult;
import com.checkup.checkup.domain.attendance.service.AttendanceService;
import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.qr.entity.QrScanResult;
import com.checkup.checkup.domain.qr.entity.QrSession;
import com.checkup.checkup.domain.qr.entity.QrToken;
import com.checkup.checkup.domain.qr.repository.QrSessionRepository;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.time.OperatingDayCalculator;
import com.checkup.checkup.support.MutableClock;

/**
 * QR 스캔 판정 순서(INVALID → EXPIRED → CLOSED → APPROVED·DUPLICATE)와 출석 기록 결과의 판정 변환을 검증한다(DEC-018).
 */
class QrScanServiceTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long STUDENT_ID = 70L;
    private static final String TOKEN = "a".repeat(43);
    private static final String SESSION_ID = "session-1";

    private final QrSessionRepository qrSessionRepository = mock(QrSessionRepository.class);
    private final AttendanceService attendanceService = mock(AttendanceService.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final MutableClock clock = new MutableClock(kst(2026, 9, 27, 21, 0));

    private final QrScanService qrScanService = new QrScanService(
            qrSessionRepository,
            attendanceService,
            studentRepository,
            clock
    );

    @BeforeEach
    void setUp() {
        Student student = mock(Student.class);
        given(student.getId()).willReturn(STUDENT_ID);
        given(student.isAttendanceEligible()).willReturn(true);
        given(studentRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(student));
    }

    @Test
    @DisplayName("학생이 아니면 403이고 토큰을 확인하지 않는다")
    void nonStudentReturnsForbiddenWithoutTokenLookup() {
        given(studentRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> qrScanService.scan(MEMBER_ID, TOKEN))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MISSING_STUDENT_INFO));
        verifyNoInteractions(qrSessionRepository, attendanceService);
    }

    @Test
    @DisplayName("호실이 없으면 403 ROOM_NOT_ASSIGNED이고 토큰을 확인하지 않는다")
    void withoutRoomIsRejectedWithRoomNotAssigned() {
        Student student = realStudent(null);
        student.agree(false, clock.instant(), "v1");

        assertScanRejected(student, ErrorCode.ROOM_NOT_ASSIGNED);
    }

    @Test
    @DisplayName("필수 동의가 없으면 403 FACE_CONSENT_REQUIRED이고 토큰을 확인하지 않는다")
    void withoutConsentIsRejectedWithConsentRequired() {
        assertScanRejected(realStudent(301), ErrorCode.FACE_CONSENT_REQUIRED);
    }

    @Test
    @DisplayName("얼굴 정보 동의만 빠져도 403 FACE_CONSENT_REQUIRED다")
    void missingFaceConsentOnlyIsRejected() {
        Student student = realStudent(301);
        student.agree(false, clock.instant(), "v1");
        ReflectionTestUtils.setField(student, "faceAgreedAt", null);

        assertScanRejected(student, ErrorCode.FACE_CONSENT_REQUIRED);
    }

    @Test
    @DisplayName("동의도 호실도 없으면 동의 오류를 먼저 돌려준다")
    void withoutConsentAndRoomReportsConsentFirst() {
        assertScanRejected(realStudent(null), ErrorCode.FACE_CONSENT_REQUIRED);
    }

    private void assertScanRejected(Student student, ErrorCode expected) {
        given(studentRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(student));

        assertThatThrownBy(() -> qrScanService.scan(MEMBER_ID, TOKEN))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
        verifyNoInteractions(qrSessionRepository, attendanceService);
    }

    private static Student realStudent(Integer dormitoryRoom) {
        return Student.create(Member.create(1L, "학생", MemberRole.STUDENT), 1L, "학생", 1, 1, 1, 1101,
                dormitoryRoom);
    }

    @Test
    @DisplayName("형식이 다른 토큰은 조회하지 않고 INVALID다")
    void malformedTokenIsInvalidWithoutLookup() {
        assertThat(qrScanService.scan(MEMBER_ID, "not-a-token")).isEqualTo(QrScanResult.INVALID);
        assertThat(qrScanService.scan(MEMBER_ID, "a".repeat(44))).isEqualTo(QrScanResult.INVALID);

        verify(qrSessionRepository, never()).findToken(anyString());
    }

    @Test
    @DisplayName("발급하지 않은 토큰은 INVALID다")
    void unknownTokenIsInvalid() {
        given(qrSessionRepository.findToken(TOKEN)).willReturn(Optional.empty());

        assertThat(qrScanService.scan(MEMBER_ID, TOKEN)).isEqualTo(QrScanResult.INVALID);
        verifyNoInteractions(attendanceService);
    }

    @Test
    @DisplayName("만료 시각이 지난 토큰은 EXPIRED다")
    void expiredTokenIsExpired() {
        given(qrSessionRepository.findToken(TOKEN)).willReturn(Optional.of(token(clock.instant())));

        assertThat(qrScanService.scan(MEMBER_ID, TOKEN)).isEqualTo(QrScanResult.EXPIRED);
        verifyNoInteractions(attendanceService);
    }

    @Test
    @DisplayName("세션이 종료됐으면 CLOSED다")
    void closedSessionIsClosed() {
        given(qrSessionRepository.findToken(TOKEN)).willReturn(Optional.of(token(clock.instant().plusSeconds(600))));
        given(qrSessionRepository.findById(SESSION_ID)).willReturn(Optional.empty());

        assertThat(qrScanService.scan(MEMBER_ID, TOKEN)).isEqualTo(QrScanResult.CLOSED);
        verifyNoInteractions(attendanceService);
    }

    @Test
    @DisplayName("세션의 lease가 끝났으면 CLOSED다")
    void expiredLeaseIsClosed() {
        given(qrSessionRepository.findToken(TOKEN)).willReturn(Optional.of(token(clock.instant().plusSeconds(600))));
        given(qrSessionRepository.findById(SESSION_ID))
                .willReturn(Optional.of(session(AttendancePurpose.DORMITORY, clock.instant())));

        assertThat(qrScanService.scan(MEMBER_ID, TOKEN)).isEqualTo(QrScanResult.CLOSED);
        verifyNoInteractions(attendanceService);
    }

    @Test
    @DisplayName("처음 출석하면 세션 용도와 현재 운영일로 기록하고 APPROVED다")
    void firstAttendanceIsApproved() {
        activeToken(AttendancePurpose.STUDY_ROOM);
        given(attendanceService.markAttended(any(), any(), any(), any())).willReturn(AttendanceRecordResult.RECORDED);

        QrScanResult result = qrScanService.scan(MEMBER_ID, TOKEN);

        assertThat(result).isEqualTo(QrScanResult.APPROVED);
        verify(attendanceService).markAttended(
                STUDENT_ID, AttendancePurpose.STUDY_ROOM, clock.instant(), AttendanceMethod.QR);
    }

    @Test
    @DisplayName("이미 출석했으면 DUPLICATE다")
    void alreadyAttendedIsDuplicate() {
        activeToken(AttendancePurpose.DORMITORY);
        given(attendanceService.markAttended(any(), any(), any(), any())).willReturn(AttendanceRecordResult.ALREADY_ATTENDED);

        assertThat(qrScanService.scan(MEMBER_ID, TOKEN)).isEqualTo(QrScanResult.DUPLICATE);
    }

    @Test
    @DisplayName("수동 수정에 밀린 인증은 DUPLICATE다")
    void supersededByManualIsDuplicate() {
        activeToken(AttendancePurpose.DORMITORY);
        given(attendanceService.markAttended(any(), any(), any(), any()))
                .willReturn(AttendanceRecordResult.SUPERSEDED_BY_MANUAL);

        assertThat(qrScanService.scan(MEMBER_ID, TOKEN)).isEqualTo(QrScanResult.DUPLICATE);
    }

    @Test
    @DisplayName("지난 운영일로 판정되면 EXPIRED다")
    void staleIsExpired() {
        activeToken(AttendancePurpose.DORMITORY);
        given(attendanceService.markAttended(any(), any(), any(), any())).willReturn(AttendanceRecordResult.STALE);

        assertThat(qrScanService.scan(MEMBER_ID, TOKEN)).isEqualTo(QrScanResult.EXPIRED);
    }

    @Test
    @DisplayName("미래 시각으로 판정되면 INVALID다")
    void futureIsInvalid() {
        activeToken(AttendancePurpose.DORMITORY);
        given(attendanceService.markAttended(any(), any(), any(), any())).willReturn(AttendanceRecordResult.FUTURE);

        assertThat(qrScanService.scan(MEMBER_ID, TOKEN)).isEqualTo(QrScanResult.INVALID);
    }

    private void activeToken(AttendancePurpose purpose) {
        given(qrSessionRepository.findToken(TOKEN)).willReturn(Optional.of(token(clock.instant().plusSeconds(60))));
        given(qrSessionRepository.findById(SESSION_ID))
                .willReturn(Optional.of(session(purpose, clock.instant().plusSeconds(60))));
    }

    private static QrToken token(Instant expiresAt) {
        return new QrToken(TOKEN, SESSION_ID, expiresAt);
    }

    private static QrSession session(AttendancePurpose purpose, Instant leaseExpiresAt) {
        return new QrSession(SESSION_ID, 1L, purpose, LocalDate.of(2026, 9, 27), TOKEN,
                leaseExpiresAt.plusSeconds(600), leaseExpiresAt);
    }

    private static Instant kst(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(OperatingDayCalculator.ZONE).toInstant();
    }
}
