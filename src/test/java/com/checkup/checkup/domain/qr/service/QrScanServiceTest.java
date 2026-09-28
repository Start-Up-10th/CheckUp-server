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
import org.junit.jupiter.api.Test;

import com.checkup.checkup.domain.attendance.entity.AttendanceMethod;
import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.attendance.entity.AttendanceRecordResult;
import com.checkup.checkup.domain.attendance.service.AttendanceService;
import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.member.service.MemberService;
import com.checkup.checkup.domain.qr.entity.QrPurpose;
import com.checkup.checkup.domain.qr.entity.QrScanResult;
import com.checkup.checkup.domain.qr.entity.QrSession;
import com.checkup.checkup.domain.qr.entity.QrToken;
import com.checkup.checkup.domain.qr.repository.QrSessionRepository;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.time.OperatingDayCalculator;
import com.checkup.checkup.support.MutableClock;

class QrScanServiceTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long STUDENT_ID = 70L;
    private static final String TOKEN = "a".repeat(43);
    private static final String SESSION_ID = "session-1";

    private final QrSessionRepository qrSessionRepository = mock(QrSessionRepository.class);
    private final AttendanceService attendanceService = mock(AttendanceService.class);
    private final MemberService memberService = mock(MemberService.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final MutableClock clock = new MutableClock(kst(2026, 9, 27, 21, 0));

    private final QrScanService qrScanService = new QrScanService(
            qrSessionRepository,
            attendanceService,
            memberService,
            studentRepository,
            clock
    );

    private final Member member = Member.create(100L, "학생", MemberRole.STUDENT);

    @BeforeEach
    void setUp() {
        Student student = mock(Student.class);
        given(student.getId()).willReturn(STUDENT_ID);
        given(memberService.getById(MEMBER_ID)).willReturn(member);
        given(studentRepository.findByMember(member)).willReturn(Optional.of(student));
    }

    @Test
    void 학생이_아니면_403이고_토큰을_확인하지_않는다() {
        given(studentRepository.findByMember(member)).willReturn(Optional.empty());

        assertThatThrownBy(() -> qrScanService.scan(MEMBER_ID, TOKEN))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MISSING_STUDENT_INFO));
        verifyNoInteractions(qrSessionRepository, attendanceService);
    }

    @Test
    void 형식이_다른_토큰은_조회하지_않고_INVALID다() {
        assertThat(qrScanService.scan(MEMBER_ID, "not-a-token")).isEqualTo(QrScanResult.INVALID);
        assertThat(qrScanService.scan(MEMBER_ID, "a".repeat(44))).isEqualTo(QrScanResult.INVALID);

        verify(qrSessionRepository, never()).findToken(anyString());
    }

    @Test
    void 발급하지_않은_토큰은_INVALID다() {
        given(qrSessionRepository.findToken(TOKEN)).willReturn(Optional.empty());

        assertThat(qrScanService.scan(MEMBER_ID, TOKEN)).isEqualTo(QrScanResult.INVALID);
        verifyNoInteractions(attendanceService);
    }

    @Test
    void 만료_시각이_지난_토큰은_EXPIRED다() {
        given(qrSessionRepository.findToken(TOKEN)).willReturn(Optional.of(token(clock.instant())));

        assertThat(qrScanService.scan(MEMBER_ID, TOKEN)).isEqualTo(QrScanResult.EXPIRED);
        verifyNoInteractions(attendanceService);
    }

    @Test
    void 세션이_종료됐으면_CLOSED다() {
        given(qrSessionRepository.findToken(TOKEN)).willReturn(Optional.of(token(clock.instant().plusSeconds(600))));
        given(qrSessionRepository.findById(SESSION_ID)).willReturn(Optional.empty());

        assertThat(qrScanService.scan(MEMBER_ID, TOKEN)).isEqualTo(QrScanResult.CLOSED);
        verifyNoInteractions(attendanceService);
    }

    @Test
    void 세션의_lease가_끝났으면_CLOSED다() {
        given(qrSessionRepository.findToken(TOKEN)).willReturn(Optional.of(token(clock.instant().plusSeconds(600))));
        given(qrSessionRepository.findById(SESSION_ID))
                .willReturn(Optional.of(session(QrPurpose.DORMITORY, clock.instant())));

        assertThat(qrScanService.scan(MEMBER_ID, TOKEN)).isEqualTo(QrScanResult.CLOSED);
        verifyNoInteractions(attendanceService);
    }

    @Test
    void 처음_출석하면_세션_용도와_현재_운영일로_기록하고_APPROVED다() {
        activeToken(QrPurpose.STUDY_ROOM);
        given(attendanceService.markAttended(any(), any(), any(), any())).willReturn(AttendanceRecordResult.RECORDED);

        QrScanResult result = qrScanService.scan(MEMBER_ID, TOKEN);

        assertThat(result).isEqualTo(QrScanResult.APPROVED);
        verify(attendanceService).markAttended(
                STUDENT_ID, AttendancePurpose.STUDY_ROOM, clock.instant(), AttendanceMethod.QR);
    }

    @Test
    void 이미_출석했으면_DUPLICATE다() {
        activeToken(QrPurpose.DORMITORY);
        given(attendanceService.markAttended(any(), any(), any(), any())).willReturn(AttendanceRecordResult.ALREADY_ATTENDED);

        assertThat(qrScanService.scan(MEMBER_ID, TOKEN)).isEqualTo(QrScanResult.DUPLICATE);
    }

    @Test
    void 수동_수정에_밀린_인증은_DUPLICATE다() {
        activeToken(QrPurpose.DORMITORY);
        given(attendanceService.markAttended(any(), any(), any(), any()))
                .willReturn(AttendanceRecordResult.SUPERSEDED_BY_MANUAL);

        assertThat(qrScanService.scan(MEMBER_ID, TOKEN)).isEqualTo(QrScanResult.DUPLICATE);
    }

    @Test
    void 지난_운영일로_판정되면_EXPIRED다() {
        activeToken(QrPurpose.DORMITORY);
        given(attendanceService.markAttended(any(), any(), any(), any())).willReturn(AttendanceRecordResult.STALE);

        assertThat(qrScanService.scan(MEMBER_ID, TOKEN)).isEqualTo(QrScanResult.EXPIRED);
    }

    @Test
    void 미래_시각으로_판정되면_INVALID다() {
        activeToken(QrPurpose.DORMITORY);
        given(attendanceService.markAttended(any(), any(), any(), any())).willReturn(AttendanceRecordResult.FUTURE);

        assertThat(qrScanService.scan(MEMBER_ID, TOKEN)).isEqualTo(QrScanResult.INVALID);
    }

    private void activeToken(QrPurpose purpose) {
        given(qrSessionRepository.findToken(TOKEN)).willReturn(Optional.of(token(clock.instant().plusSeconds(60))));
        given(qrSessionRepository.findById(SESSION_ID))
                .willReturn(Optional.of(session(purpose, clock.instant().plusSeconds(60))));
    }

    private static QrToken token(Instant expiresAt) {
        return new QrToken(TOKEN, SESSION_ID, expiresAt);
    }

    private static QrSession session(QrPurpose purpose, Instant leaseExpiresAt) {
        return new QrSession(SESSION_ID, 1L, purpose, LocalDate.of(2026, 9, 27), TOKEN,
                leaseExpiresAt.plusSeconds(600), leaseExpiresAt);
    }

    private static Instant kst(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(OperatingDayCalculator.ZONE).toInstant();
    }
}
