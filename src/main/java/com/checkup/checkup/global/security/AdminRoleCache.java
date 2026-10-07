package com.checkup.checkup.global.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.checkup.checkup.domain.member.entity.MemberRole;

import lombok.RequiredArgsConstructor;

/**
 * 회원 id별 역할을 짧게 서버 메모리에 보관해, 관리자 API마다 회원 행을 DB에서 읽지 않게 한다(#212).
 * 역할은 로그인과 DataGSM 동기화에서만 바뀌므로, 바꾸는 쪽이 커밋 뒤에 {@link #evictAfterCommit}으로 항목을 지운다.
 * 그래서 서버가 한 대면 권한 회수가 바로 반영된다. 지우지 못하는 경우(다른 서버 인스턴스)도 {@link #TTL}이 지나면 반영된다.
 * 없는 회원은 보관하지 않는다. DB·Redis에는 저장하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class AdminRoleCache {

    static final Duration TTL = Duration.ofSeconds(30);
    static final int MAX_ENTRIES = 1000;

    private record Entry(MemberRole role, Instant expiresAt) {
    }

    private final Clock clock;
    private final ConcurrentMap<Long, Entry> entries = new ConcurrentHashMap<>();

    /** 보관한 역할을 돌려준다. 없거나 {@link #TTL}이 지났으면 비어 있다. */
    public Optional<MemberRole> get(Long memberId) {
        Entry entry = entries.get(memberId);
        if (entry == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(entry.expiresAt())) {
            entries.remove(memberId, entry);
            return Optional.empty();
        }
        return Optional.of(entry.role());
    }

    /** 역할을 보관한다. 가득 차면 만료분을 먼저 지우고, 그래도 가득 차 있으면 보관하지 않는다. */
    public void put(Long memberId, MemberRole role) {
        Instant now = clock.instant();
        if (entries.size() >= MAX_ENTRIES) {
            entries.values().removeIf(entry -> !now.isBefore(entry.expiresAt()));
            if (entries.size() >= MAX_ENTRIES) {
                return;
            }
        }
        entries.put(memberId, new Entry(role, now.plus(TTL)));
    }

    /** 항목을 바로 지운다. */
    public void evict(Long memberId) {
        entries.remove(memberId);
    }

    /**
     * 트랜잭션이 커밋된 뒤 항목을 지운다. 트랜잭션 밖이면 바로 지운다.
     * 커밋 전에 지우면 그 사이 다른 요청이 옛 역할을 다시 보관할 수 있어 커밋 뒤에 지운다.
     */
    public void evictAfterCommit(Long memberId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evict(memberId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evict(memberId);
            }
        });
    }
}
