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
    void 필수_두_항목에_동의하면_204다() throws Exception {
        mockMvc.perform(post(CONSENT)
                        .with(loginAs(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"privacy\":true,\"face\":true,\"noticeAlarm\":true}"))
                .andExpect(status().isNoContent());

        verify(consentService).agree(MEMBER_ID, true);
    }

    @Test
    void 공지_알림을_받지_않아도_필수_동의만_있으면_204다() throws Exception {
        mockMvc.perform(post(CONSENT)
                        .with(loginAs(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"privacy\":true,\"face\":true,\"noticeAlarm\":false}"))
                .andExpect(status().isNoContent());

        verify(consentService).agree(MEMBER_ID, false);
    }

    @Test
    void 필수_항목에_동의하지_않으면_400이고_저장하지_않는다() throws Exception {
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
    void 항목이_빠지면_400이다() throws Exception {
        mockMvc.perform(post(CONSENT)
                        .with(loginAs(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"privacy\":true,\"face\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("noticeAlarm"));

        verify(consentService, never()).agree(any(), anyBoolean());
    }

    @Test
    void 로그인하지_않으면_401이다() throws Exception {
        mockMvc.perform(post(CONSENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"privacy\":true,\"face\":true,\"noticeAlarm\":true}"))
                .andExpect(status().isUnauthorized());

        verify(consentService, never()).agree(any(), anyBoolean());
    }

    @Test
    void 학생이_아니면_403과_오류_코드다() throws Exception {
        willThrow(new CustomException(ErrorCode.MISSING_STUDENT_INFO)).given(consentService).agree(MEMBER_ID, true);

        mockMvc.perform(post(CONSENT)
                        .with(loginAs(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"privacy\":true,\"face\":true,\"noticeAlarm\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MISSING_STUDENT_INFO"));
    }

    @Test
    void 요청에_다른_회원_id를_넣어도_로그인한_회원으로_처리한다() throws Exception {
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
