package com.checkup.checkup.domain.face.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 필터 순서에서 얼굴 등록 요청이 본문을 받기 전에 로그인한 학생의 등록 가능 여부로 걸러지는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FaceEnrollmentPrecheckIntegrationTest {

    private static final String ENROLLMENT_PATH = "/api/v1/face/enrollments";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("필수 동의가 없는 학생은 본문 형식을 보기 전에 403 FACE_CONSENT_REQUIRED를 받는다")
    void studentWithoutConsentIsRejectedBeforeBodyIsHandled() throws Exception {
        Long memberId = jdbcTemplate.queryForObject(
                "INSERT INTO member (datagsm_id, name, role) VALUES (?, '학생', 'STUDENT') RETURNING id",
                Long.class, nextDataGsmId());
        jdbcTemplate.update("""
                INSERT INTO student
                    (member_id, datagsm_student_id, name, number, grade, class_number, student_number, dormitory_room)
                VALUES (?, ?, '학생', 1, 1, 1, 1101, 301)
                """, memberId, nextDataGsmId());

        // 컨트롤러까지 가면 지원하지 않는 본문 형식이라 415가 난다. 그 전에 걸러져야 403이다.
        // MockMvc는 servletPath를 비워 두므로 필터가 경로를 알아보도록 실제 서버처럼 채운다.
        mockMvc.perform(post(ENROLLMENT_PATH)
                        .servletPath(ENROLLMENT_PATH)
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of())))
                        .contentType("text/plain")
                        .content(new byte[]{1, 2, 3}))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FACE_CONSENT_REQUIRED"));
    }

    private static Long nextDataGsmId() {
        return ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
    }
}
