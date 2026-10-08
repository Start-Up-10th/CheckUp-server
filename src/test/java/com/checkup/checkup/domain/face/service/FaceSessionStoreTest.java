package com.checkup.checkup.domain.face.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * 저장되지 않은 학생이 섞이면 세션째 취소되는지 검증한다(#216).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(FaceSessionStore.class)
class FaceSessionStoreTest {

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

        sessionStore.create(sessionId, adminMemberId, AttendancePurpose.DORMITORY, studentIds,
                Instant.now(), Instant.now());

        assertThat(sessionStore.findOwned(sessionId, adminMemberId).candidateStudentIds())
                .containsExactlyInAnyOrderElementsOf(studentIds);
        assertThat(sessionStore.isOwnedActive(sessionId, adminMemberId)).isTrue();
    }

    @Test
    @DisplayName("저장되지 않은 학생이 섞이면 FACE_NO_ENROLLED_STUDENTS이고 세션과 후보를 하나도 남기지 않는다")
    void unknownStudentCancelsWholeSession() {
        UUID sessionId = UUID.randomUUID();
        List<Long> withMissing = new ArrayList<>(studentIds);
        withMissing.add(-1L);

        assertThatThrownBy(() -> sessionStore.create(sessionId, adminMemberId, AttendancePurpose.DORMITORY,
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
}
