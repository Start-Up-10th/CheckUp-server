package com.checkup.checkup.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.support.MutableClock;

/**
 * 관리자 역할 기억이 만료·가득 참·커밋 뒤 삭제에서 옛 역할을 오래 남기지 않는지 검증한다(#212).
 */
class AdminRoleCacheTest {

    private MutableClock clock;
    private AdminRoleCache cache;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-07T00:00:00Z"));
        cache = new AdminRoleCache(clock);
    }

    @Test
    @DisplayName("보관한 역할을 기억 시간 동안 돌려주고 시간이 지나면 비운다")
    void expiresAfterTtl() {
        cache.put(1L, MemberRole.ADMIN);

        assertThat(cache.get(1L)).contains(MemberRole.ADMIN);
        clock.advance(AdminRoleCache.TTL);
        assertThat(cache.get(1L)).isEmpty();
    }

    @Test
    @DisplayName("가득 차면 새 항목을 보관하지 않고, 기존 항목이 만료되면 만료분을 치우고 보관한다")
    void fullCacheRefusesNewUntilExpiredEntriesAreDropped() {
        for (long id = 0; id < AdminRoleCache.MAX_ENTRIES; id++) {
            cache.put(id, MemberRole.STUDENT);
        }
        cache.put(-1L, MemberRole.ADMIN);
        assertThat(cache.get(-1L)).isEmpty();

        clock.advance(AdminRoleCache.TTL);
        cache.put(-1L, MemberRole.ADMIN);
        assertThat(cache.get(-1L)).contains(MemberRole.ADMIN);
    }

    @Test
    @DisplayName("트랜잭션 안에서는 커밋 뒤에야 항목을 지운다")
    void evictsOnlyAfterCommitInsideTransaction() {
        cache.put(1L, MemberRole.ADMIN);
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.evictAfterCommit(1L);
            assertThat(cache.get(1L)).contains(MemberRole.ADMIN);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            assertThat(cache.get(1L)).isEmpty();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("롤백되면 항목을 지우지 않는다")
    void doesNotEvictOnRollback() {
        cache.put(1L, MemberRole.ADMIN);
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.evictAfterCommit(1L);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(cache.get(1L)).contains(MemberRole.ADMIN);
    }

    @Test
    @DisplayName("트랜잭션 밖에서는 바로 지운다")
    void evictsImmediatelyOutsideTransaction() {
        cache.put(1L, MemberRole.ADMIN);

        cache.evictAfterCommit(1L);

        assertThat(cache.get(1L)).isEmpty();
    }
}
