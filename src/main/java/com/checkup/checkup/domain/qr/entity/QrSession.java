package com.checkup.checkup.domain.qr.entity;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 관리자 QR 페이지 하나의 세션. Redis에만 두며 발급 이력으로 남기지 않는다.
 *
 * @param id              세션 ID
 * @param adminId         세션을 만든 관리자의 member id
 * @param purpose         출석 용도
 * @param operatingDay    현재 토큰이 속한 운영일
 * @param token           현재 표시 중인 토큰
 * @param tokenExpiresAt  현재 토큰의 만료 시각
 * @param leaseExpiresAt  heartbeat가 없으면 세션이 끝나는 시각
 */
public record QrSession(
        String id,
        Long adminId,
        QrPurpose purpose,
        LocalDate operatingDay,
        String token,
        Instant tokenExpiresAt,
        Instant leaseExpiresAt
) {

    /**
     * lease 만료 시각만 바꾼 세션을 반환한다.
     */
    public QrSession withLease(Instant leaseExpiresAt) {
        return new QrSession(id, adminId, purpose, operatingDay, token, tokenExpiresAt, leaseExpiresAt);
    }

    /**
     * 새 토큰으로 바꾼 세션을 반환한다.
     */
    public QrSession withToken(LocalDate operatingDay, String token, Instant tokenExpiresAt) {
        return new QrSession(id, adminId, purpose, operatingDay, token, tokenExpiresAt, leaseExpiresAt);
    }

    /**
     * 주어진 관리자가 만든 세션인지 확인한다.
     */
    public boolean isOwnedBy(Long adminId) {
        return this.adminId.equals(adminId);
    }

    /**
     * 주어진 시각에 lease가 남아 있는지 확인한다.
     */
    public boolean isLeaseActive(Instant now) {
        return now.isBefore(leaseExpiresAt);
    }
}
