package com.checkup.checkup.domain.qr.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.checkup.checkup.domain.qr.config.QrConfig;
import com.checkup.checkup.domain.qr.dto.QrSessionIssue;
import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.qr.service.QrSessionService;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.security.AdminVerifier;
import com.checkup.checkup.global.security.SecurityConfig;

/**
 * 관리자 QR API가 관리자만 세션을 만들고 유지·종료하게 하며, 계약의 상태 코드와 오류 코드로 응답하는지 검증한다(REQ-ATT-003·004).
 */
@WebMvcTest(QrController.class)
@Import({SecurityConfig.class, QrConfig.class})
class QrControllerTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long STUDENT_ID = 2L;
    private static final String TOKEN = "a".repeat(43);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QrSessionService qrSessionService;

    @MockitoBean
    private AdminVerifier adminVerifier;

    private final QrSessionIssue issue = new QrSessionIssue(
            "session-1",
            AttendancePurpose.DORMITORY,
            TOKEN,
            Instant.parse("2026-09-27T03:15:00Z"),
            Instant.parse("2026-09-27T03:01:00Z"),
            Instant.parse("2026-09-27T03:00:00Z")
    );

    @Test
    @DisplayName("관리자는 QR 세션을 만들고 201과 qrUrl을 받는다")
    void adminCreatesSessionWithQrUrl() throws Exception {
        given(qrSessionService.create(ADMIN_ID, AttendancePurpose.DORMITORY)).willReturn(issue);

        mockMvc.perform(post("/api/v1/qr")
                        .with(loginAs(ADMIN_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"DORMITORY\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value("session-1"))
                .andExpect(jsonPath("$.purpose").value("DORMITORY"))
                .andExpect(jsonPath("$.qrUrl").value("http://localhost:3000/qr#t=" + TOKEN))
                .andExpect(jsonPath("$.tokenExpiresAt").value("2026-09-27T03:15:00Z"))
                .andExpect(jsonPath("$.leaseExpiresAt").value("2026-09-27T03:01:00Z"))
                .andExpect(jsonPath("$.serverTime").value("2026-09-27T03:00:00Z"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    @DisplayName("로그인하지 않으면 401이다")
    void withoutLoginReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/qr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"DORMITORY\"}"))
                .andExpect(status().isUnauthorized());

        verify(qrSessionService, never()).create(any(), any());
    }

    @Test
    @DisplayName("관리자가 아니면 403이고 세션을 만들지 않는다")
    void nonAdminReturnsForbiddenWithoutSession() throws Exception {
        willThrow(new CustomException(ErrorCode.ADMIN_ONLY)).given(adminVerifier).verify(STUDENT_ID);

        mockMvc.perform(post("/api/v1/qr")
                        .with(loginAs(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"DORMITORY\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ONLY"));

        verify(qrSessionService, never()).create(any(), any());
    }

    @Test
    @DisplayName("용도가 없거나 잘못되면 400이다")
    void missingOrInvalidPurposeReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/qr")
                        .with(loginAs(ADMIN_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/qr")
                        .with(loginAs(ADMIN_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"GYM\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("heartbeat는 현재 qrUrl을 돌려준다")
    void heartbeatReturnsCurrentQrUrl() throws Exception {
        given(qrSessionService.heartbeat(ADMIN_ID, "session-1")).willReturn(issue);

        mockMvc.perform(post("/api/v1/qr/session-1/heartbeat").with(loginAs(ADMIN_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrUrl").value("http://localhost:3000/qr#t=" + TOKEN));
    }

    @Test
    @DisplayName("없는 세션의 heartbeat는 404다")
    void heartbeatOnUnknownSessionReturnsNotFound() throws Exception {
        given(qrSessionService.heartbeat(ADMIN_ID, "gone"))
                .willThrow(new CustomException(ErrorCode.QR_SESSION_NOT_FOUND));

        mockMvc.perform(post("/api/v1/qr/gone/heartbeat").with(loginAs(ADMIN_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QR_SESSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("종료는 204이고 해당 세션만 종료한다")
    void closeReturnsNoContentForThatSessionOnly() throws Exception {
        mockMvc.perform(post("/api/v1/qr/session-1/close").with(loginAs(ADMIN_ID)))
                .andExpect(status().isNoContent());

        verify(qrSessionService).close(ADMIN_ID, "session-1");
    }

    private static RequestPostProcessor loginAs(Long memberId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of()));
    }
}
