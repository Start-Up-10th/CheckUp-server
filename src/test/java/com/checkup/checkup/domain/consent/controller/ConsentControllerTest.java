package com.checkup.checkup.domain.consent.controller;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
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

import com.checkup.checkup.domain.consent.service.ConsentService;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.security.SecurityConfig;

/**
 * 동의 API가 필수 두 항목을 검증하고, 로그인한 학생 본인의 동의만 저장하며, 401·403·400을 공통 오류 형식으로 응답하는지 검증한다(REQ-AUTH-004).
 */
@WebMvcTest(ConsentController.class)
@Import(SecurityConfig.class)
class ConsentControllerTest {

    private static final Long MEMBER_ID = 7L;
    private static final String CONSENT = "/api/v1/consent";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsentService consentService;

    @Test
    @DisplayName("필수 두 항목에 동의하면 204다")
    void requiredConsentReturnsNoContent() throws Exception {
        mockMvc.perform(post(CONSENT)
                        .with(loginAs(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"privacy\":true,\"face\":true,\"noticeAlarm\":true}"))
                .andExpect(status().isNoContent());

        verify(consentService).agree(MEMBER_ID, true);
    }

    @Test
    @DisplayName("공지 알림을 받지 않아도 필수 동의만 있으면 204다")
    void requiredConsentWithoutNoticeAlarmReturnsNoContent() throws Exception {
        mockMvc.perform(post(CONSENT)
                        .with(loginAs(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"privacy\":true,\"face\":true,\"noticeAlarm\":false}"))
                .andExpect(status().isNoContent());

        verify(consentService).agree(MEMBER_ID, false);
    }

    @Test
    @DisplayName("필수 항목에 동의하지 않으면 400이고 저장하지 않는다")
    void missingRequiredConsentReturnsBadRequest() throws Exception {
        mockMvc.perform(post(CONSENT)
                        .with(loginAs(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"privacy\":true,\"face\":false,\"noticeAlarm\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("face"));

        verify(consentService, never()).agree(any(), anyBoolean());
    }

    @Test
    @DisplayName("항목이 빠지면 400이다")
    void missingFieldReturnsBadRequest() throws Exception {
        mockMvc.perform(post(CONSENT)
                        .with(loginAs(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"privacy\":true,\"face\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("noticeAlarm"));

        verify(consentService, never()).agree(any(), anyBoolean());
    }

    @Test
    @DisplayName("로그인하지 않으면 401이다")
    void withoutLoginReturnsUnauthorized() throws Exception {
        mockMvc.perform(post(CONSENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"privacy\":true,\"face\":true,\"noticeAlarm\":true}"))
                .andExpect(status().isUnauthorized());

        verify(consentService, never()).agree(any(), anyBoolean());
    }

    @Test
    @DisplayName("학생이 아니면 403과 오류 코드다")
    void nonStudentReturnsForbidden() throws Exception {
        willThrow(new CustomException(ErrorCode.MISSING_STUDENT_INFO)).given(consentService).agree(MEMBER_ID, true);

        mockMvc.perform(post(CONSENT)
                        .with(loginAs(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"privacy\":true,\"face\":true,\"noticeAlarm\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MISSING_STUDENT_INFO"));
    }

    @Test
    @DisplayName("요청에 다른 회원 id를 넣어도 로그인한 회원으로 처리한다")
    void usesLoggedInMemberEvenIfBodyHasOtherId() throws Exception {
        mockMvc.perform(post(CONSENT)
                        .with(loginAs(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"privacy\":true,\"face\":true,\"noticeAlarm\":true,\"memberId\":999}"))
                .andExpect(status().isNoContent());

        verify(consentService).agree(MEMBER_ID, true);
    }

    private static RequestPostProcessor loginAs(Long memberId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of()));
    }
}
