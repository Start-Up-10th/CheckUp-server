package com.checkup.checkup.domain.qr.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.checkup.checkup.domain.attendance.entity.AttendanceMethod;
import com.checkup.checkup.domain.attendance.entity.AttendanceRecordResult;
import com.checkup.checkup.domain.attendance.service.AttendanceService;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.qr.entity.QrScanResult;
import com.checkup.checkup.domain.qr.entity.QrSession;
import com.checkup.checkup.domain.qr.entity.QrToken;
import com.checkup.checkup.domain.qr.repository.QrSessionRepository;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 학생의 QR 스캔을 판정하고 출석을 기록한다(REQ-ATT-002·005, 하네스 DEC-018).
 *
 * 판정 순서: 모르는 토큰({@code INVALID}) → 토큰 만료({@code EXPIRED})
 * → 세션 종료·lease 만료({@code CLOSED}) → 출석 저장({@code APPROVED}/{@code DUPLICATE}).
 * 운영일은 출석 서비스가 스캔 시각으로 계산한다.
 * 출석할 학생은 요청 값이 아니라 로그인 세션의 회원으로 정한다.
 */
@Service
@RequiredArgsConstructor
public class QrScanService {

    private final QrSessionRepository qrSessionRepository;
    private final AttendanceService attendanceService;
    private final StudentRepository studentRepository;
    private final Clock clock;

    /**
     * QR 토큰으로 현재 학생의 출석을 처리한다.
     *
     * @param memberId 로그인 세션의 회원 id
     * @param token    QR 링크의 토큰
     * @return 판정 결과
     * @throws CustomException 학생이 아닌 회원이면 {@link ErrorCode#MISSING_STUDENT_INFO}(403)
     */
    public QrScanResult scan(Long memberId, String token) {
        Student student = findStudent(memberId);
        Instant now = clock.instant();

        Optional<QrToken> qrToken = QrTokenGenerator.isWellFormed(token)
                ? qrSessionRepository.findToken(token)
                : Optional.empty();
        if (qrToken.isEmpty()) {
            return QrScanResult.INVALID;
        }
        if (!now.isBefore(qrToken.get().expiresAt())) {
            return QrScanResult.EXPIRED;
        }

        Optional<QrSession> session = qrSessionRepository.findById(qrToken.get().sessionId())
                .filter(found -> found.isLeaseActive(now));
        if (session.isEmpty()) {
            return QrScanResult.CLOSED;
        }

        AttendanceRecordResult recorded = attendanceService.markAttended(
                student.getId(),
                session.get().purpose(),
                now,
                AttendanceMethod.QR
        );
        return toScanResult(recorded);
    }

    private static QrScanResult toScanResult(AttendanceRecordResult recorded) {
        return switch (recorded) {
            case RECORDED -> QrScanResult.APPROVED;
            case ALREADY_ATTENDED, SUPERSEDED_BY_MANUAL -> QrScanResult.DUPLICATE;
            case STALE -> QrScanResult.EXPIRED;
            case FUTURE -> QrScanResult.INVALID;
        };
    }

    private Student findStudent(Long memberId) {
        return studentRepository.findByMemberId(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MISSING_STUDENT_INFO));
    }
}
