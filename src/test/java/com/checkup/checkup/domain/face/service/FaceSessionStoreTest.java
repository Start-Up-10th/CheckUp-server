package com.checkup.checkup.domain.face.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;

/**
 * 실제 PostgreSQL에서 얼굴 인식 세션 저장이 후보 학생을 한 번에 저장하고,
 * 저장되지 않은 학생이 섞이면 세션째 취소되는지, 용도·층별 활성 세션을 찾는지,
 * 프레임 최소 간격을 이전 프레임 시작부터 세는지 검증한다(#216, #260, #262).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(FaceSessionStore.class)
class FaceSessionStoreTest {

    private static final Instant T0 = Instant.parse("2100-01-01T00:00:00Z");
    private static final Duration INTERVAL = Duration.ofMillis(200);

    @Autowired
    private FaceSessionStore sessionStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long adminMemberId;
    private final List<Long> memberIds = new ArrayList<>();
    private final List<Long> studentIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        long base = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE / 2);
        adminMemberId = jdbcTemplate.queryForObject(
                "INSERT INTO member (datagsm_id, name, role) VALUES (?, '관리자', 'ADMIN') RETURNING id",
                Long.class, base);
        for (int i = 0; i < 3; i++) {
            Long memberId = jdbcTemplate.queryForObject(
                    "INSERT INTO member (datagsm_id, name, role) VALUES (?, ?, 'STUDENT') RETURNING id",
                    Long.class, base + 1 + i, "학생" + i);
            Long studentId = jdbcTemplate.queryForObject(
                    "INSERT INTO student (member_id, name, number, grade, class_number, student_number, "
                            + "dormitory_room, privacy_agreed_at, face_agreed_at) "
                            + "VALUES (?, ?, 1, 1, 1, ?, 301, now(), now()) RETURNING id",
                    Long.class, memberId, "학생" + i, 1100 + i);
            memberIds.add(memberId);
            studentIds.add(studentId);
        }
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM face_recognition_session WHERE admin_member_id = ?", adminMemberId);
        studentIds.forEach(id -> jdbcTemplate.update("DELETE FROM student WHERE id = ?", id));
        memberIds.forEach(id -> jdbcTemplate.update("DELETE FROM member WHERE id = ?", id));
        jdbcTemplate.update("DELETE FROM member WHERE id = ?", adminMemberId);
    }

    @Test
    @DisplayName("세션을 저장하면 후보 학생 모두가 한 번에 저장되고 같은 세션으로 조회된다")
    void savesAllCandidates() {
        UUID sessionId = UUID.randomUUID();

        sessionStore.create(sessionId, adminMemberId, AttendancePurpose.DORMITORY, 4, studentIds,
                Instant.now(), Instant.now());

        FaceSessionView saved = sessionStore.findOwned(sessionId, adminMemberId);
        assertThat(saved.candidateStudentIds()).containsExactlyInAnyOrderElementsOf(studentIds);
        assertThat(saved.floor()).isEqualTo(4);
        assertThat(sessionStore.isOwnedActive(sessionId, adminMemberId)).isTrue();
    }

    @Test
    @DisplayName("저장되지 않은 학생이 섞이면 FACE_NO_ENROLLED_STUDENTS이고 세션과 후보를 하나도 남기지 않는다")
    void unknownStudentCancelsWholeSession() {
        UUID sessionId = UUID.randomUUID();
        List<Long> withMissing = new ArrayList<>(studentIds);
        withMissing.add(-1L);

        assertThatThrownBy(() -> sessionStore.create(sessionId, adminMemberId, AttendancePurpose.DORMITORY, 4,
                withMissing, Instant.now(), Instant.now()))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FACE_NO_ENROLLED_STUDENTS));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM face_recognition_session WHERE session_id = ?", Integer.class, sessionId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM face_recognition_session_candidate WHERE session_id = ?",
                Integer.class, sessionId)).isZero();
    }

    @Test
    @DisplayName("같은 용도·층의 활성 세션만 찾고 층 없는 세션은 모든 층으로 센다")
    void findsActiveSessionByPurposeAndFloor() {
        Instant recent = Instant.parse("2100-01-01T00:10:00Z");
        Instant cutoff = Instant.parse("2100-01-01T00:05:00Z");
        sessionStore.create(UUID.randomUUID(), adminMemberId, AttendancePurpose.DORMITORY, 3, studentIds, recent, recent);

        assertThat(sessionStore.existsActiveOnFloor(AttendancePurpose.DORMITORY, 3, cutoff)).isTrue();
        assertThat(sessionStore.existsActiveOnFloor(AttendancePurpose.DORMITORY, 4, cutoff)).isFalse();
        assertThat(sessionStore.existsActiveOnFloor(AttendancePurpose.STUDY_ROOM, 3, cutoff)).isFalse();

        sessionStore.create(UUID.randomUUID(), adminMemberId, AttendancePurpose.STUDY_ROOM, null, studentIds, recent, recent);

        assertThat(sessionStore.existsActiveOnFloor(AttendancePurpose.STUDY_ROOM, 5, cutoff)).isTrue();
    }

    @Test
    @DisplayName("새 세션의 첫 프레임은 바로 받는다")
    void firstFrameIsClaimedImmediately() {
        UUID sessionId = newSession(T0);

        assertThat(claim(sessionId, T0)).isTrue();
    }

    @Test
    @DisplayName("프레임 간격은 이전 프레임 시작부터 세어 199ms는 거절하고 200ms는 받는다")
    void intervalCountsFromPreviousFrameStart() {
        UUID sessionId = newSession(T0);
        UUID first = UUID.randomUUID();
        assertThat(sessionStore.claimFrame(sessionId, adminMemberId, T0, T0.minus(INTERVAL), first,
                T0.plusSeconds(60))).isTrue();
        sessionStore.releaseFrame(sessionId, adminMemberId, first, T0.plusMillis(150));

        assertThat(claim(sessionId, T0.plusMillis(199))).isFalse();
        assertThat(claim(sessionId, T0.plusMillis(200))).isTrue();
    }

    @Test
    @DisplayName("처리가 간격보다 오래 걸렸으면 끝난 뒤 바로 다음 프레임을 받는다")
    void slowFrameAllowsNextFrameRightAfterRelease() {
        UUID sessionId = newSession(T0);
        UUID first = UUID.randomUUID();
        assertThat(sessionStore.claimFrame(sessionId, adminMemberId, T0, T0.minus(INTERVAL), first,
                T0.plusSeconds(60))).isTrue();
        sessionStore.releaseFrame(sessionId, adminMemberId, first, T0.plusMillis(300));

        assertThat(claim(sessionId, T0.plusMillis(300))).isTrue();
    }

    @Test
    @DisplayName("이전 프레임이 아직 처리 중이면 간격이 지나도 거절한다")
    void inFlightFrameBlocksNextFrame() {
        UUID sessionId = newSession(T0);
        assertThat(claim(sessionId, T0)).isTrue();

        assertThat(claim(sessionId, T0.plusMillis(250))).isFalse();
    }

    private UUID newSession(Instant createdAt) {
        UUID sessionId = UUID.randomUUID();
        sessionStore.create(sessionId, adminMemberId, AttendancePurpose.DORMITORY, 3, studentIds, createdAt, createdAt);
        return sessionId;
    }

    private boolean claim(UUID sessionId, Instant now) {
        return sessionStore.claimFrame(sessionId, adminMemberId, now, now.minus(INTERVAL), UUID.randomUUID(),
                now.plusSeconds(60));
    }

    @Test
    @DisplayName("유휴 시간이 지났거나 닫힌 세션은 찾지 않는다")
    void ignoresIdleOrClosedSession() {
        Instant cutoff = Instant.parse("2100-01-01T00:05:00Z");
        sessionStore.create(UUID.randomUUID(), adminMemberId, AttendancePurpose.DORMITORY, 3, studentIds, cutoff, cutoff);
        UUID closed = UUID.randomUUID();
        Instant recent = Instant.parse("2100-01-01T00:10:00Z");
        sessionStore.create(closed, adminMemberId, AttendancePurpose.DORMITORY, 3, studentIds, recent, recent);
        sessionStore.markInactive(closed, adminMemberId);

        assertThat(sessionStore.existsActiveOnFloor(AttendancePurpose.DORMITORY, 3, cutoff)).isFalse();
    }
}
