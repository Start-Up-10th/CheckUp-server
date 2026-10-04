package com.checkup.checkup.domain.face.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.checkup.checkup.domain.face.ai.AiFaceClient;
import com.checkup.checkup.domain.face.config.FaceProperties;
import com.checkup.checkup.domain.member.dto.StudentLeftEvent;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.attendance.service.AttendanceService;
import com.checkup.checkup.domain.face.repository.FaceTemplateRepository;
import com.checkup.checkup.global.security.AdminVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 학생이 졸업·자퇴하면 이벤트 커밋 뒤에 얼굴 벡터와 인식 세션 후보가 삭제되는지 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({FaceEnrollmentStore.class, FaceSessionStore.class, FaceStudentLeftListener.class,
        FaceStudentLeftListenerTest.TestConfig.class})
class FaceStudentLeftListenerTest {
    @TestConfiguration
    static class TestConfig {
        @Bean
        AiFaceClient aiFaceClient() {
            return mock(AiFaceClient.class);
        }

        @Bean
        FaceRecognitionService faceRecognitionService(
                FaceTemplateRepository templateRepository,
                FaceSessionStore sessionStore,
                AiFaceClient aiFaceClient,
                StudentRepository studentRepository
        ) {
            return new FaceRecognitionService(templateRepository, sessionStore, aiFaceClient,
                    studentRepository, mock(AttendanceService.class), mock(AdminVerifier.class),
                    new FaceProperties("http://face-ai.test", "test-token", Duration.ofSeconds(2),
                            Duration.ofSeconds(5), 1024, 512, Duration.ofMillis(200), 2,
                            Duration.ofMinutes(5), 60_000, "v1"),
                    tools.jackson.databind.json.JsonMapper.builder().build(),
                    Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), ZoneOffset.UTC));
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private AiFaceClient aiFaceClient;

    private Long adminMemberId;
    private Long studentMemberId;
    private Long studentId;
    private UUID sessionId;

    @Test
    @DisplayName("퇴사 이벤트 커밋 후 벡터와 세션 후보가 삭제된다")
    void studentLeftDeletesTemplateAndCandidatesAfterCommit() {
        insertFaceData();

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status ->
                eventPublisher.publishEvent(new StudentLeftEvent(studentId, "GRADUATE")));

        assertThat(count("face_template", "student_id", studentId)).isZero();
        assertThat(count("face_recognition_session_candidate", "session_id", sessionId)).isZero();
        assertThat(count("face_recognition_session", "session_id", sessionId)).isZero();
        verify(aiFaceClient).deleteSession(sessionId);
    }

    @AfterEach
    void cleanUp() {
        if (sessionId != null) {
            jdbcTemplate.update("DELETE FROM face_recognition_session_candidate WHERE session_id = ?", sessionId);
            jdbcTemplate.update("DELETE FROM face_recognition_session WHERE session_id = ?", sessionId);
        }
        if (studentId != null) {
            jdbcTemplate.update("DELETE FROM face_template WHERE student_id = ?", studentId);
            jdbcTemplate.update("DELETE FROM student WHERE id = ?", studentId);
        }
        if (studentMemberId != null) {
            jdbcTemplate.update("DELETE FROM member WHERE id = ?", studentMemberId);
        }
        if (adminMemberId != null) {
            jdbcTemplate.update("DELETE FROM member WHERE id = ?", adminMemberId);
        }
    }

    private void insertFaceData() {
        adminMemberId = insertMember("ADMIN");
        studentMemberId = insertMember("STUDENT");
        studentId = jdbcTemplate.queryForObject("""
                INSERT INTO student (member_id, name, number, grade, class_number, student_number, dormitory_room,
                                     face_agreed_at)
                VALUES (?, '테스트학생', 1, 1, 1, 1101, 301, now())
                RETURNING id
                """, Long.class, studentMemberId);
        jdbcTemplate.update("""
                INSERT INTO face_template (student_id, model_id, model_version, dimension, normalization, vectors_json)
                VALUES (?, 'model-a', 'v1', 256, 'l2', '[]')
                """, studentId);

        sessionId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO face_recognition_session (session_id, admin_member_id, purpose, created_at,
                                                      last_activity_at, active)
                VALUES (?, ?, 'DORMITORY', now(), now(), TRUE)
                """, sessionId, adminMemberId);
        jdbcTemplate.update("""
                INSERT INTO face_recognition_session_candidate (session_id, student_id)
                VALUES (?, ?)
                """, sessionId, studentId);
    }

    private Long insertMember(String role) {
        long datagsmId = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
        return jdbcTemplate.queryForObject("""
                INSERT INTO member (datagsm_id, name, role)
                VALUES (?, 'Face cleanup test', ?)
                RETURNING id
                """, Long.class, datagsmId, role);
    }

    private int count(String table, String column, Object value) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class, value);
    }
}
