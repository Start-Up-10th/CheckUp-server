package com.checkup.checkup.global.querylog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * 쿼리 수 로그를 켜면 요청마다 실제 PostgreSQL 쿼리 수가 한 줄로 남고 쿼리 문자열은 남지 않는지 검증한다.
 */
@SpringBootTest(properties = "checkup.query-log.enabled=true")
@AutoConfigureMockMvc
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class QueryLogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long adminId;

    @BeforeEach
    void setUp() {
        adminId = insertMember("관리자", "ADMIN");
        insertStudent("가학생", 1101, 301);
        insertStudent("나학생", 1102, 301);
        insertStudent("다학생", 1103, 302);
    }

    @Test
    @DisplayName("호실 출석 명단 조회는 학생 수와 상관없이 쿼리 3번이다")
    void roomAttendanceRunsThreeQueries(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/v1/room/attendance")
                        .param("dormitoryRoom", "301")
                        .with(loginAs(adminId)))
                .andExpect(status().isOk());

        assertThat(output).contains("GET /api/v1/room/attendance status=200 queries=3 elapsedMs=");
    }

    @Test
    @DisplayName("층 단위 출석 현황 조회는 호실 수와 상관없이 쿼리 2번이다")
    void floorRunsTwoQueries(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/v1/room/floor")
                        .param("floor", "3")
                        .with(loginAs(adminId)))
                .andExpect(status().isOk());

        assertThat(output).contains("GET /api/v1/room/floor status=200 queries=2 elapsedMs=");
    }

    @Test
    @DisplayName("로그에 쿼리 문자열을 남기지 않는다")
    void doesNotLogQueryString(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/v1/room/floor")
                        .param("floor", "3")
                        .queryParam("marker", "do-not-log-this")
                        .with(loginAs(adminId)))
                .andExpect(status().isOk());

        assertThat(output).contains("GET /api/v1/room/floor status=200");
        assertThat(output).doesNotContain("do-not-log-this");
    }

    @Test
    @DisplayName("상태 확인 요청은 로그에 남기지 않는다")
    void doesNotLogHealthCheck(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        assertThat(output).doesNotContain("GET /actuator/health");
    }

    private Long insertMember(String name, String role) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO member (datagsm_id, name, role) VALUES (?, ?, ?) RETURNING id",
                Long.class, nextDataGsmId(), name, role);
    }

    private void insertStudent(String name, int studentNumber, int room) {
        jdbcTemplate.update("""
                INSERT INTO student
                    (datagsm_student_id, name, number, grade, class_number, student_number, dormitory_room)
                VALUES (?, ?, 1, 1, 1, ?, ?)
                """, nextDataGsmId(), name, studentNumber, room);
    }

    private static Long nextDataGsmId() {
        return ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
    }

    private static RequestPostProcessor loginAs(Long memberId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of()));
    }
}
