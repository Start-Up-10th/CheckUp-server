package com.checkup.checkup.domain.user.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

import com.checkup.checkup.domain.user.dto.response.UserSearchResponse;

import lombok.RequiredArgsConstructor;

/**
 * DataGSM 학생 조회 결과를 짧게 보관해 같은 학생의 반복 조회가 외부 API를 매번 부르지 않게 한다.
 * 이메일이 들어 있으므로 서버 메모리에만 {@link #TTL} 동안 두고 DB·Redis에 저장하지 않는다.
 * 실패·없음 응답은 보관하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class UserSearchCache {

    static final Duration TTL = Duration.ofSeconds(60);
    static final int MAX_ENTRIES = 1000;

    private record Entry(UserSearchResponse response, Instant expiresAt) {
    }

    private final Clock clock;
    private final ConcurrentMap<Long, Entry> entries = new ConcurrentHashMap<>();

    public Optional<UserSearchResponse> get(Long studentId) {
        Entry entry = entries.get(studentId);
        if (entry == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(entry.expiresAt())) {
            entries.remove(studentId, entry);
            return Optional.empty();
        }
        return Optional.of(entry.response());
    }

    public void put(Long studentId, UserSearchResponse response) {
        Instant now = clock.instant();
        if (entries.size() >= MAX_ENTRIES) {
            entries.values().removeIf(entry -> !now.isBefore(entry.expiresAt()));
            if (entries.size() >= MAX_ENTRIES) {
                return;
            }
        }
        entries.put(studentId, new Entry(response, now.plus(TTL)));
    }
}
