package com.checkup.checkup.domain.face.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.face.entity.FaceRecognitionSession;
import com.checkup.checkup.domain.face.service.FaceSessionStore;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 실제 PostgreSQL에서 프레임 시작 간격, 세션별 원자적 잠금, 만료 lease와 늦은 token,
 * 완료 시각에 따른 idle 만료를 검증한다(REQ-FACE-004·005). 원본 얼굴이나 실제 벡터는 사용하지 않는다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(FaceSessionStore.class)
class FaceRecognitionSessionRepositoryTest {
    private static final Instant T0 = Instant.parse("2026-10-07T00:00:00Z");
    private static final Duration INTERVAL = Duration.ofMillis(200);
    private static final String TEST_SCHEMA = "face_cadence_it_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void useIsolatedSchema(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> TEST_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> TEST_SCHEMA);
        registry.add("spring.flyway.create-schemas", () -> true);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> TEST_SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + TEST_SCHEMA);
    }

    @Autowired
    private FaceRecognitionSessionRepository repository;

    @Autowired
    private FaceSessionStore store;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    private final List<Long> adminIds = new ArrayList<>();
    private Long adminId;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        adminId = newAdmin();
        sessionId = newSession();
    }

    @AfterEach
    void cleanUpMembersAndTheirSessions() {
        adminIds.forEach(id -> jdbcTemplate.update("DELETE FROM member WHERE id = ?", id));
        adminIds.clear();
    }

    @AfterAll
    void removeOnlyThisTestSchema() {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + TEST_SCHEMA + " CASCADE");
    }

    @Test
    @DisplayName("새 세션은 활동 시각을 생성 시각으로 저장하고 첫 프레임을 바로 허용한다")
    void freshSessionAllowsImmediateFirstFrame() {
        UUID freshId = UUID.randomUUID();
        store.create(freshId, adminId, AttendancePurpose.DORMITORY, List.of(), T0);

        FaceRecognitionSession fresh = read(freshId);
        assertThat(fresh.getLastActivityAt()).isEqualTo(T0);
        assertThat(fresh.getLastFrameStartedAt()).isNull();
        assertThat(claim(freshId, adminId, T0, UUID.randomUUID(), T0.plusSeconds(2))).isEqualTo(1);
        assertThat(read(freshId).getLastFrameStartedAt()).isEqualTo(T0);
    }

    @Test
    @DisplayName("50ms 처리 후 다음 프레임은 처음 시작한 때의 199ms에는 거절하고 200ms에 허용한다")
    void shortFrameWaitsUntilStartIntervalBoundary() {
        UUID token = UUID.randomUUID();
        assertThat(claim(sessionId, adminId, T0, token, T0.plusSeconds(2))).isEqualTo(1);
        assertThat(release(sessionId, token, T0.plusMillis(50))).isEqualTo(1);
        assertThat(read(sessionId).getLastActivityAt()).isEqualTo(T0.plusMillis(50));
        assertThat(read(sessionId).getLastFrameStartedAt()).isEqualTo(T0);

        assertThat(claim(sessionId, adminId, T0.plusMillis(199), UUID.randomUUID(), T0.plusSeconds(2))).isZero();
        assertThat(read(sessionId).getLastFrameStartedAt()).isEqualTo(T0);
        assertThat(claim(sessionId, adminId, T0.plusMillis(200), UUID.randomUUID(), T0.plusSeconds(2))).isEqualTo(1);
    }

    @Test
    @DisplayName("300ms 처리 후 다음 프레임은 완료 직후 허용해 추가 200ms 대기를 없앤다")
    void longFrameAllowsNextFrameImmediatelyAfterCompletion() {
        UUID token = UUID.randomUUID();
        assertThat(claim(sessionId, adminId, T0, token, T0.plusSeconds(2))).isEqualTo(1);
        assertThat(release(sessionId, token, T0.plusMillis(300))).isEqualTo(1);

        assertThat(claim(sessionId, adminId, T0.plusMillis(300), UUID.randomUUID(), T0.plusSeconds(2))).isEqualTo(1);
        assertThat(read(sessionId).getLastFrameStartedAt()).isEqualTo(T0.plusMillis(300));
    }

    @Test
    @DisplayName("시작 간격이 지나도 활성 lease 동안 거절하고 만료 경계에서만 새 잠금을 잡는다")
    void leaseProtectsInflightFrameUntilExpiry() {
        UUID oldToken = UUID.randomUUID();
        assertThat(claim(sessionId, adminId, T0, oldToken, T0.plusSeconds(2))).isEqualTo(1);
        assertThat(claim(sessionId, adminId, T0.plusMillis(200), UUID.randomUUID(), T0.plusSeconds(3))).isZero();
        assertThat(claim(sessionId, adminId, T0.plusMillis(1_999), UUID.randomUUID(), T0.plusSeconds(3))).isZero();
        UUID newToken = UUID.randomUUID();
        assertThat(claim(sessionId, adminId, T0.plusSeconds(2), newToken, T0.plusSeconds(4))).isEqualTo(1);

        assertThat(release(sessionId, oldToken, T0.plusSeconds(3))).isZero();
        assertThat(extend(sessionId, oldToken, T0.plusSeconds(3), T0.plusSeconds(9))).isZero();
        assertThat(read(sessionId).getFrameLockToken()).isEqualTo(newToken);
        assertThat(read(sessionId).getFrameLockUntil()).isEqualTo(T0.plusSeconds(4));
        assertThat(read(sessionId).getLastFrameStartedAt()).isEqualTo(T0.plusSeconds(2));
    }

    @Test
    @DisplayName("404 복구 lease를 연장해도 같은 프레임의 시작 시각은 바뀌지 않는다")
    void recoveryExtensionKeepsOriginalFrameStart() {
        UUID token = UUID.randomUUID();
        claim(sessionId, adminId, T0, token, T0.plusSeconds(2));

        assertThat(extend(sessionId, token, T0.plusSeconds(1), T0.plusSeconds(4))).isEqualTo(1);
        assertThat(read(sessionId).getLastFrameStartedAt()).isEqualTo(T0);
        assertThat(claim(sessionId, adminId, T0.plusSeconds(2), UUID.randomUUID(), T0.plusSeconds(5))).isZero();
        assertThat(release(sessionId, token, T0.plusSeconds(3))).isEqualTo(1);
        assertThat(claim(sessionId, adminId, T0.plusSeconds(3), UUID.randomUUID(), T0.plusSeconds(5))).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 세션을 독립 트랜잭션 두 개가 동시에 요청하면 한 프레임만 허용한다")
    void concurrentTransactionsClaimSameSessionOnlyOnce() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentClaim(sessionId, ready, start));
            var second = executor.submit(() -> concurrentClaim(sessionId, ready, start));
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(1, 0);
        }
    }

    @Test
    @DisplayName("다른 카메라 세션은 독립 트랜잭션에서 동시에 프레임을 처리할 수 있다")
    void differentSessionsCanBeClaimedConcurrently() throws Exception {
        UUID otherId = newSession();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentClaim(sessionId, ready, start));
            var second = executor.submit(() -> concurrentClaim(otherId, ready, start));
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsOnly(1);
        }
    }

    @Test
    @DisplayName("다른 관리자와 종료된 세션은 프레임 잠금을 잡을 수 없다")
    void wrongOwnerAndClosedSessionCannotClaim() {
        assertThat(claim(sessionId, newAdmin(), T0, UUID.randomUUID(), T0.plusSeconds(2))).isZero();
        assertThat(read(sessionId).getLastFrameStartedAt()).isNull();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> repository.markInactive(sessionId, adminId));

        assertThat(claim(sessionId, adminId, T0, UUID.randomUUID(), T0.plusSeconds(2))).isZero();
        assertThat(read(sessionId).getLastFrameStartedAt()).isNull();
    }

    @Test
    @DisplayName("idle 정리는 활성 lease를 보호하고 마지막 완료 시각부터 만료를 판단한다")
    void idleCleanupUsesCompletionTimeAndProtectsLease() {
        UUID token = UUID.randomUUID();
        claim(sessionId, adminId, T0, token, T0.plusSeconds(600));
        Instant now = T0.plusSeconds(301);
        Instant cutoff = now.minusSeconds(300);

        assertThat(repository.findIdleBefore(cutoff, now)).extracting(FaceRecognitionSession::getId)
                .doesNotContain(sessionId);
        int protectedCount = new TransactionTemplate(transactionManager)
                .execute(status -> repository.markInactiveIfIdle(sessionId, cutoff, now));
        assertThat(protectedCount).isZero();

        release(sessionId, token, now);
        assertThat(read(sessionId).getLastActivityAt()).isEqualTo(now);
        assertThat(read(sessionId).getLastFrameStartedAt()).isEqualTo(T0);
        assertThat(repository.findIdleBefore(cutoff, now)).extracting(FaceRecognitionSession::getId)
                .doesNotContain(sessionId);
        Instant later = now.plusSeconds(301);
        int expiredCount = new TransactionTemplate(transactionManager)
                .execute(status -> repository.markInactiveIfIdle(sessionId, later.minusSeconds(300), later));
        assertThat(expiredCount).isEqualTo(1);
    }

    @Test
    @DisplayName("V16 세션을 V17로 갱신하면 활동 시각으로 시작 시각을 채우고 후보와 잠금을 보존한다")
    void migrationBackfillsExistingSessionsWithoutChangingCandidatesOrLocks() {
        String legacySchema = "face_migration_it_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure().dataSource(dataSource).schemas(legacySchema).defaultSchema(legacySchema)
                    .target("16").load().migrate();
            Long owner = jdbcTemplate.queryForObject("INSERT INTO " + legacySchema + ".member "
                    + "(datagsm_id, name, role) VALUES (1, 'Migration test', 'ADMIN') RETURNING id", Long.class);
            Long student = jdbcTemplate.queryForObject("INSERT INTO " + legacySchema + ".student "
                    + "(name, number, grade, class_number, student_number) "
                    + "VALUES ('Migration test', 1, 1, 1, 1101) RETURNING id", Long.class);
            UUID oldId = UUID.randomUUID();
            UUID oldToken = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO " + legacySchema + ".face_recognition_session "
                    + "(session_id, admin_member_id, purpose, created_at, last_activity_at, active, frame_lock_token, frame_lock_until) "
                    + "VALUES (?, ?, 'DORMITORY', ?, ?, TRUE, ?, ?)",
                    oldId, owner, Timestamp.from(T0.minusSeconds(1)), Timestamp.from(T0), oldToken, Timestamp.from(T0.plusSeconds(2)));
            jdbcTemplate.update("INSERT INTO " + legacySchema + ".face_recognition_session_candidate "
                    + "(session_id, student_id) VALUES (?, ?)", oldId, student);
            UUID inactiveId = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO " + legacySchema + ".face_recognition_session "
                    + "(session_id, admin_member_id, purpose, created_at, last_activity_at, active) "
                    + "VALUES (?, ?, 'STUDY_ROOM', ?, ?, FALSE)",
                    inactiveId, owner, Timestamp.from(T0), Timestamp.from(T0.plusSeconds(1)));

            var migrated = Flyway.configure().dataSource(dataSource).schemas(legacySchema).defaultSchema(legacySchema)
                    .load().migrate();

            assertThat(migrated.migrationsExecuted).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT last_frame_started_at FROM " + legacySchema
                    + ".face_recognition_session WHERE session_id = ?", Timestamp.class, oldId).toInstant()).isEqualTo(T0);
            assertThat(jdbcTemplate.queryForObject("SELECT last_activity_at FROM " + legacySchema
                    + ".face_recognition_session WHERE session_id = ?", Timestamp.class, oldId).toInstant()).isEqualTo(T0);
            assertThat(jdbcTemplate.queryForObject("SELECT frame_lock_token FROM " + legacySchema
                    + ".face_recognition_session WHERE session_id = ?", UUID.class, oldId)).isEqualTo(oldToken);
            assertThat(jdbcTemplate.queryForObject("SELECT frame_lock_until FROM " + legacySchema
                    + ".face_recognition_session WHERE session_id = ?", Timestamp.class, oldId).toInstant())
                    .isEqualTo(T0.plusSeconds(2));
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + legacySchema
                    + ".face_recognition_session_candidate WHERE session_id = ?", Integer.class, oldId)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT last_frame_started_at FROM " + legacySchema
                    + ".face_recognition_session WHERE session_id = ?", Timestamp.class, inactiveId).toInstant())
                    .isEqualTo(T0.plusSeconds(1));
            assertThat(jdbcTemplate.queryForObject("SELECT active FROM " + legacySchema
                    + ".face_recognition_session WHERE session_id = ?", Boolean.class, inactiveId)).isFalse();
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + legacySchema + " CASCADE");
        }
    }

    private int concurrentClaim(UUID id, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("test claim start timed out");
        }
        return claim(id, adminId, T0, UUID.randomUUID(), T0.plusSeconds(2));
    }

    private int claim(UUID id, Long owner, Instant now, UUID token, Instant lockUntil) {
        return new TransactionTemplate(transactionManager).execute(status ->
                repository.claimFrame(id, owner, now, now.minus(INTERVAL), token, lockUntil));
    }

    private int release(UUID id, UUID token, Instant now) {
        return new TransactionTemplate(transactionManager).execute(status -> repository.releaseFrame(id, adminId, token, now));
    }

    private int extend(UUID id, UUID token, Instant now, Instant lockUntil) {
        return new TransactionTemplate(transactionManager).execute(status -> repository.extendFrame(id, adminId, token, now, lockUntil));
    }

    private FaceRecognitionSession read(UUID id) {
        return repository.findById(id).orElseThrow();
    }

    private UUID newSession() {
        UUID id = UUID.randomUUID();
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                repository.saveAndFlush(FaceRecognitionSession.create(id, adminId, AttendancePurpose.DORMITORY, T0)));
        return id;
    }

    private Long newAdmin() {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO member (datagsm_id, name, role) VALUES (?, 'Face cadence test', 'ADMIN') RETURNING id
                """, Long.class, ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE));
        adminIds.add(id);
        return id;
    }
}
