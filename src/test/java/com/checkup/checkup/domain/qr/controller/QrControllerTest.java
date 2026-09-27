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
import com.checkup.checkup.domain.qr.entity.QrPurpose;
import com.checkup.checkup.domain.qr.service.QrSessionService;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.security.AdminVerifier;
import com.checkup.checkup.global.security.SecurityConfig;

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
            QrPurpose.DORMITORY,
            TOKEN,
            Instant.parse("2026-09-27T03:15:00Z"),
            Instant.parse("2026-09-27T03:01:00Z"),
            Instant.parse("2026-09-27T03:00:00Z")
    );

    @Test
    void 관리자는_QR_세션을_만들고_201과_qrUrl을_받는다() throws Exception {
        given(qrSessionService.create(ADMIN_ID, QrPurpose.DORMITORY)).willReturn(issue);

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
    void 로그인하지_않으면_401이다() throws Exception {
        mockMvc.perform(post("/api/v1/qr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"DORMITORY\"}"))
                .andExpect(status().isUnauthorized());

        verify(qrSessionService, never()).create(any(), any());
    }

    @Test
    void 관리자가_아니면_403이고_세션을_만들지_않는다() throws Exception {
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
    void 용도가_없거나_잘못되면_400이다() throws Exception {
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
    void heartbeat는_현재_qrUrl을_돌려준다() throws Exception {
        given(qrSessionService.heartbeat(ADMIN_ID, "session-1")).willReturn(issue);

        mockMvc.perform(post("/api/v1/qr/session-1/heartbeat").with(loginAs(ADMIN_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrUrl").value("http://localhost:3000/qr#t=" + TOKEN));
    }

    @Test
    void 없는_세션의_heartbeat는_404다() throws Exception {
        given(qrSessionService.heartbeat(ADMIN_ID, "gone"))
                .willThrow(new CustomException(ErrorCode.QR_SESSION_NOT_FOUND));

        mockMvc.perform(post("/api/v1/qr/gone/heartbeat").with(loginAs(ADMIN_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QR_SESSION_NOT_FOUND"));
    }

    @Test
    void 종료는_204이고_해당_세션만_종료한다() throws Exception {
        mockMvc.perform(post("/api/v1/qr/session-1/close").with(loginAs(ADMIN_ID)))
                .andExpect(status().isNoContent());

        verify(qrSessionService).close(ADMIN_ID, "session-1");
    }

    private static RequestPostProcessor loginAs(Long memberId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of()));
    }
}
