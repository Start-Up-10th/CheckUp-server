package com.checkup.checkup.domain.qr.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.checkup.checkup.domain.qr.config.QrProperties;
import com.checkup.checkup.domain.qr.dto.QrSessionIssue;
import com.checkup.checkup.domain.qr.entity.QrPurpose;
import com.checkup.checkup.domain.qr.entity.QrSession;
import com.checkup.checkup.domain.qr.entity.QrToken;
import com.checkup.checkup.domain.qr.repository.QrSessionRepository;
import com.checkup.checkup.global.time.OperatingDayCalculator;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 QR 페이지별 세션과 토큰을 관리한다(REQ-ATT-003·004, DEC-007).
 *
 * <p>세션은 페이지마다 독립적이며 다른 관리자·탭의 세션에 영향을 주지 않는다.
 * 토큰은 발급 때 정한 만료 시각을 바꾸지 않고, 새 토큰으로 교체만 한다.
 */
@Service
@RequiredArgsConstructor
public class QrSessionService {

    private final QrSessionRepository qrSessionRepository;
    private final QrTokenGenerator qrTokenGenerator;
    private final OperatingDayCalculator operatingDayCalculator;
    private final QrProperties qrProperties;
    private final Clock clock;

    /**
     * 새 QR 세션을 만들고 첫 토큰을 발급한다.
     *
     * @param adminId 세션을 만드는 관리자의 member id
     * @param purpose 출석 용도
     */
    public QrSessionIssue create(Long adminId, QrPurpose purpose) {
        Instant now = clock.instant();
        QrSession session = issueToken(new QrSession(
                UUID.randomUUID().toString(),
                adminId,
                purpose,
                null,
                null,
                null,
                now.plus(qrProperties.leaseTtl())
        ), now);
        qrSessionRepository.save(session, qrProperties.leaseTtl());
        return QrSessionIssue.of(session, now);
    }

    /**
     * lease를 연장하고 현재 토큰을 반환한다.
     * 토큰 만료가 가까웠거나 운영일이 바뀌었으면 새 토큰으로 교체한다.
     *
     * @throws ResponseStatusException 세션이 없거나 lease가 끝났거나 다른 관리자의 세션이면 404
     */
    public QrSessionIssue heartbeat(Long adminId, String sessionId) {
        Instant now = clock.instant();
        QrSession session = qrSessionRepository.findById(sessionId)
                .filter(found -> found.isOwnedBy(adminId))
                .filter(found -> found.isLeaseActive(now))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "QR 세션을 찾을 수 없습니다."))
                .withLease(now.plus(qrProperties.leaseTtl()));

        if (needsRotation(session, now)) {
            session = issueToken(session, now);
        }
        qrSessionRepository.save(session, qrProperties.leaseTtl());
        return QrSessionIssue.of(session, now);
    }

    /**
     * 세션을 종료한다. 이미 없는 세션이나 다른 관리자의 세션이면 아무것도 하지 않는다.
     */
    public void close(Long adminId, String sessionId) {
        qrSessionRepository.findById(sessionId)
                .filter(session -> session.isOwnedBy(adminId))
                .ifPresent(qrSessionRepository::delete);
    }

    /**
     * 관리자가 만든 모든 세션을 종료한다. 로그아웃할 때 사용한다.
     */
    public void closeAll(Long adminId) {
        qrSessionRepository.findSessionIdsByAdmin(adminId)
                .forEach(sessionId -> close(adminId, sessionId));
    }

    private boolean needsRotation(QrSession session, Instant now) {
        if (!session.operatingDay().equals(operatingDayCalculator.today())) {
            return true;
        }
        boolean nearExpiry = !now.isBefore(session.tokenExpiresAt().minus(qrProperties.rotateBefore()));
        boolean canExtend = session.tokenExpiresAt().isBefore(operatingDayCalculator.nextBoundary());
        return nearExpiry && canExtend;
    }

    private QrSession issueToken(QrSession session, Instant now) {
        LocalDate operatingDay = operatingDayCalculator.today();
        Instant expiresAt = earlier(now.plus(qrProperties.tokenTtl()), operatingDayCalculator.nextBoundary());
        QrToken token = new QrToken(qrTokenGenerator.generate(), session.id(), expiresAt);
        qrSessionRepository.saveToken(token, Duration.between(now, expiresAt));
        return session.withToken(operatingDay, token.token(), expiresAt);
    }

    private static Instant earlier(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
