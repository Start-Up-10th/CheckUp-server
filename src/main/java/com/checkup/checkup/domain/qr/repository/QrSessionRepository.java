package com.checkup.checkup.domain.qr.repository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.checkup.checkup.domain.qr.entity.QrPurpose;
import com.checkup.checkup.domain.qr.entity.QrSession;
import com.checkup.checkup.domain.qr.entity.QrToken;

import lombok.RequiredArgsConstructor;

/**
 * QR 세션·토큰을 Redis에 저장한다. 모든 키는 TTL로 스스로 사라진다.
 *
 * <ul>
 *     <li>{@code qr:session:{id}}: 세션 해시</li>
 *     <li>{@code qr:token:{token}}: 토큰 해시(세션 ID, 만료 시각)</li>
 *     <li>{@code qr:admin:{adminId}}: 관리자가 만든 세션 ID 집합</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class QrSessionRepository {

    private static final String SESSION_KEY = "qr:session:";
    private static final String TOKEN_KEY = "qr:token:";
    private static final String ADMIN_KEY = "qr:admin:";

    private final StringRedisTemplate redisTemplate;

    /**
     * 세션을 저장하고 관리자별 세션 집합에 추가한다.
     *
     * @param ttl 세션 키가 Redis에 남는 시간
     */
    public void save(QrSession session, Duration ttl) {
        String key = SESSION_KEY + session.id();
        redisTemplate.opsForHash().putAll(key, Map.of(
                "adminId", session.adminId().toString(),
                "purpose", session.purpose().name(),
                "operatingDay", session.operatingDay().toString(),
                "token", session.token(),
                "tokenExpiresAt", String.valueOf(session.tokenExpiresAt().toEpochMilli()),
                "leaseExpiresAt", String.valueOf(session.leaseExpiresAt().toEpochMilli())
        ));
        redisTemplate.expire(key, ttl);

        String adminKey = ADMIN_KEY + session.adminId();
        redisTemplate.opsForSet().add(adminKey, session.id());
        redisTemplate.expire(adminKey, ttl);
    }

    /**
     * 세션을 조회한다.
     */
    public Optional<QrSession> findById(String id) {
        Map<Object, Object> hash = redisTemplate.opsForHash().entries(SESSION_KEY + id);
        if (hash.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new QrSession(
                id,
                Long.valueOf((String) hash.get("adminId")),
                QrPurpose.valueOf((String) hash.get("purpose")),
                LocalDate.parse((String) hash.get("operatingDay")),
                (String) hash.get("token"),
                Instant.ofEpochMilli(Long.parseLong((String) hash.get("tokenExpiresAt"))),
                Instant.ofEpochMilli(Long.parseLong((String) hash.get("leaseExpiresAt")))
        ));
    }

    /**
     * 세션을 삭제하고 관리자별 세션 집합에서 뺀다. 발급된 토큰은 세션이 없으면 쓸 수 없다.
     */
    public void delete(QrSession session) {
        redisTemplate.delete(SESSION_KEY + session.id());
        redisTemplate.opsForSet().remove(ADMIN_KEY + session.adminId(), session.id());
    }

    /**
     * 관리자가 만든 세션 ID를 조회한다. 이미 만료된 세션 ID가 섞여 있을 수 있다.
     */
    public Set<String> findSessionIdsByAdmin(Long adminId) {
        Set<String> ids = redisTemplate.opsForSet().members(ADMIN_KEY + adminId);
        return ids == null ? Set.of() : ids;
    }

    /**
     * 토큰을 저장한다.
     *
     * @param ttl 토큰 키가 Redis에 남는 시간
     */
    public void saveToken(QrToken token, Duration ttl) {
        String key = TOKEN_KEY + token.token();
        redisTemplate.opsForHash().putAll(key, Map.of(
                "sessionId", token.sessionId(),
                "expiresAt", String.valueOf(token.expiresAt().toEpochMilli())
        ));
        redisTemplate.expire(key, ttl);
    }

    /**
     * 토큰을 조회한다.
     */
    public Optional<QrToken> findToken(String token) {
        Map<Object, Object> hash = redisTemplate.opsForHash().entries(TOKEN_KEY + token);
        if (hash.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new QrToken(
                token,
                (String) hash.get("sessionId"),
                Instant.ofEpochMilli(Long.parseLong((String) hash.get("expiresAt")))
        ));
    }
}
