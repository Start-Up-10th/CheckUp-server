package com.checkup.checkup.domain.qr.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.checkup.checkup.domain.qr.entity.QrScanResult;
import com.checkup.checkup.domain.qr.service.QrScanService;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.security.SecurityConfig;

@WebMvcTest(QrScanController.class)
@Import(SecurityConfig.class)
class QrScanControllerTest {

    private static final Long STUDENT_ID = 7L;
    private static final String TOKEN = "a".repeat(43);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QrScanService qrScanService;

    @Test
    @DisplayName("판정 결과를 200으로 돌려준다")
    void returnsResultWithOk() throws Exception {
        given(qrScanService.scan(STUDENT_ID, TOKEN)).willReturn(QrScanResult.APPROVED);

        mockMvc.perform(post("/api/v1/qr/attendance")
                        .with(loginAs(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("APPROVED"));
    }

    @Test
    @DisplayName("만료 같은 판정도 200이다")
    void expiredResultIsAlsoOk() throws Exception {
        given(qrScanService.scan(STUDENT_ID, TOKEN)).willReturn(QrScanResult.EXPIRED);

        mockMvc.perform(post("/api/v1/qr/attendance")
                        .with(loginAs(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("EXPIRED"));
    }

    @Test
    @DisplayName("요청에 다른 학생 id를 넣어도 로그인한 학생으로 처리한다")
    void usesLoggedInStudentEvenIfBodyHasOtherId() throws Exception {
        given(qrScanService.scan(STUDENT_ID, TOKEN)).willReturn(QrScanResult.APPROVED);

        mockMvc.perform(post("/api/v1/qr/attendance")
                        .with(loginAs(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\",\"studentId\":999,\"success\":true}"))
                .andExpect(status().isOk());

        verify(qrScanService).scan(STUDENT_ID, TOKEN);
    }

    @Test
    @DisplayName("로그인하지 않으면 401이다")
    void withoutLoginReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/qr/attendance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\"}"))
                .andExpect(status().isUnauthorized());

        verify(qrScanService, never()).scan(any(), any());
    }

    @Test
    @DisplayName("학생이 아니면 403과 오류 코드다")
    void nonStudentReturnsForbidden() throws Exception {
        given(qrScanService.scan(STUDENT_ID, TOKEN)).willThrow(new CustomException(ErrorCode.MISSING_STUDENT_INFO));

        mockMvc.perform(post("/api/v1/qr/attendance")
                        .with(loginAs(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MISSING_STUDENT_INFO"));
    }

    @Test
    @DisplayName("토큰이 없으면 400이다")
    void missingTokenReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/qr/attendance")
                        .with(loginAs(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(qrScanService, never()).scan(any(), any());
    }

    private static RequestPostProcessor loginAs(Long memberId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of()));
    }
}
