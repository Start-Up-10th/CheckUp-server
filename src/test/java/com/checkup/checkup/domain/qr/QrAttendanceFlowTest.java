package com.checkup.checkup.domain.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

/**
 * 관리자 QR 발급부터 학생 스캔 출석까지 실제 Redis·Postgres·Security로 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class QrAttendanceFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Long adminId;
    private Long studentMemberId;
    private Long studentId;
    private Long teacherId;

    @BeforeEach
    void setUp() {
        adminId = insertMember("관리자", "ADMIN");
        teacherId = insertMember("교사", "ADMIN");
        studentMemberId = insertMember("학생", "STUDENT");
        studentId = jdbcTemplate.queryForObject(
                "INSERT INTO student (member_id, name, number, grade, class_number, student_number) VALUES (?, '테스트학생', 1, 1, 1, 1101) RETURNING id",
                Long.class, studentMemberId);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM attendance WHERE student_id = ?", studentId);
        jdbcTemplate.update("DELETE FROM student WHERE id = ?", studentId);
        jdbcTemplate.update("DELETE FROM member WHERE id IN (?, ?, ?)", adminId, teacherId, studentMemberId);
        Set<String> keys = redisTemplate.keys("qr:*");
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("관리자가 만든 QR로 학생이 출석하고 다시 찍으면 중복이다")
    void studentAttendsWithAdminQrAndRescanIsDuplicate() throws Exception {
        String token = createQr("DORMITORY").token();

        scan(studentMemberId, token, "APPROVED");
        scan(studentMemberId, token, "DUPLICATE");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM attendance WHERE student_id = ? AND purpose = 'DORMITORY' AND attended AND method = 'QR'",
                Integer.class, studentId)).isEqualTo(1);
    }

    @Test
    @DisplayName("기숙사와 자습실 QR은 출석을 따로 기록한다")
    void dormitoryAndStudyRoomQrRecordSeparately() throws Exception {
        scan(studentMemberId, createQr("DORMITORY").token(), "APPROVED");
        scan(studentMemberId, createQr("STUDY_ROOM").token(), "APPROVED");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM attendance WHERE student_id = ?", Integer.class, studentId)).isEqualTo(2);
    }

    @Test
    @DisplayName("관리자가 QR 화면을 닫으면 CLOSED이고 출석하지 않는다")
    void closedQrReturnsClosedWithoutAttendance() throws Exception {
        CreatedQr qr = createQr("DORMITORY");
        mockMvc.perform(post("/api/v1/qr/" + qr.sessionId() + "/close").with(loginAs(adminId)))
                .andExpect(status().isNoContent());

        scan(studentMemberId, qr.token(), "CLOSED");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM attendance WHERE student_id = ?", Integer.class, studentId)).isZero();
    }

    @Test
    @DisplayName("발급하지 않은 토큰은 INVALID다")
    void unknownTokenIsInvalid() throws Exception {
        scan(studentMemberId, "b".repeat(43), "INVALID");
    }

    @Test
    @DisplayName("학생 정보가 없는 계정은 403이다")
    void accountWithoutStudentReturnsForbidden() throws Exception {
        String token = createQr("DORMITORY").token();

        mockMvc.perform(post("/api/v1/qr/attendance")
                        .with(loginAs(teacherId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MISSING_STUDENT_INFO"));
    }

    @Test
    @DisplayName("학생은 QR을 만들 수 없다")
    void studentCannotCreateQr() throws Exception {
        mockMvc.perform(post("/api/v1/qr")
                        .with(loginAs(studentMemberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"DORMITORY\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ONLY"));
    }

    @Test
    @DisplayName("닫힌 QR 세션의 heartbeat는 404와 오류 코드다")
    void heartbeatOnClosedSessionReturnsNotFound() throws Exception {
        CreatedQr qr = createQr("DORMITORY");
        mockMvc.perform(post("/api/v1/qr/" + qr.sessionId() + "/close").with(loginAs(adminId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/qr/" + qr.sessionId() + "/heartbeat").with(loginAs(adminId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QR_SESSION_NOT_FOUND"));
    }

    private CreatedQr createQr(String purpose) throws Exception {
        String body = mockMvc.perform(post("/api/v1/qr")
                        .with(loginAs(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"" + purpose + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String qrUrl = JsonPath.read(body, "$.qrUrl");
        assertThat(qrUrl).contains("/qr#t=");
        return new CreatedQr(JsonPath.read(body, "$.sessionId"), qrUrl.substring(qrUrl.indexOf("#t=") + 3));
    }

    private void scan(Long memberId, String token, String expected) throws Exception {
        mockMvc.perform(post("/api/v1/qr/attendance")
                        .with(loginAs(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(expected));
    }

    private Long insertMember(String name, String role) {
        long datagsmId = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
        return jdbcTemplate.queryForObject(
                "INSERT INTO member (datagsm_id, name, role) VALUES (?, ?, ?) RETURNING id",
                Long.class, datagsmId, name, role);
    }

    private static RequestPostProcessor loginAs(Long memberId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of()));
    }

    private record CreatedQr(String sessionId, String token) {
    }
}
