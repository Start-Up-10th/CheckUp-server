package com.checkup.checkup.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학생 본인 출석 조회와 미확인 알림 조회가 같은 회원의 두 번째 요청부터 실제 PostgreSQL 쿼리를 한 번만 보내는지 검증한다.
 */
@SpringBootTest(properties = "checkup.query-log.enabled=true")
@AutoConfigureMockMvc
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class StudentIdCacheIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long studentMemberId;
    private Long teacherMemberId;

    @BeforeEach
    void setUp() {
        studentMemberId = insertMember("학생", "STUDENT");
        jdbcTemplate.update("""
                INSERT INTO student
                    (member_id, datagsm_student_id, name, number, grade, class_number, student_number, dormitory_room)
                VALUES (?, ?, '학생', 1, 1, 1, 1101, 301)
                """, studentMemberId, nextDataGsmId());
        teacherMemberId = insertMember("교사", "TEACHER");
    }

    @Test
    @DisplayName("본인 출석 조회는 처음에 쿼리 2번, 다음부터 1번이다")
    void myAttendanceRunsOneQueryAfterFirstRequest(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/v1/attendance/me").with(loginAs(studentMemberId)))
                .andExpect(status().isOk());
        assertThat(output).contains("GET /api/v1/attendance/me status=200 queries=2 elapsedMs=");

        mockMvc.perform(get("/api/v1/attendance/me").with(loginAs(studentMemberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dormitory.attended").value(false));
        assertThat(output).contains("GET /api/v1/attendance/me status=200 queries=1 elapsedMs=");
    }

    @Test
    @DisplayName("기억한 학생 id는 다른 API에서도 써서 미확인 알림 조회가 쿼리 1번이다")
    void unreadReusesStudentIdFoundByAnotherApi(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/v1/attendance/me").with(loginAs(studentMemberId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications/unread").with(loginAs(studentMemberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasUnread").value(false));
        assertThat(output).contains("GET /api/v1/notifications/unread status=200 queries=1 elapsedMs=");
    }

    @Test
    @DisplayName("학생이 아닌 회원은 다시 요청해도 403 MISSING_STUDENT_INFO다")
    void nonStudentStaysRejected() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/v1/attendance/me").with(loginAs(teacherMemberId)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("MISSING_STUDENT_INFO"));
        }
    }

    private Long insertMember(String name, String role) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO member (datagsm_id, name, role) VALUES (?, ?, ?) RETURNING id",
                Long.class, nextDataGsmId(), name, role);
    }

    private static Long nextDataGsmId() {
        return ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
    }

    private static RequestPostProcessor loginAs(Long memberId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of()));
    }
}
