package com.checkup.checkup.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.checkup.checkup.domain.auth.service.AuthService;
import com.checkup.checkup.domain.auth.service.LoginResult;
import com.checkup.checkup.domain.auth.service.LoginSessionService;
import com.checkup.checkup.domain.consent.service.ConsentService;
import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.service.MemberService;
import com.checkup.checkup.global.config.WebConfig;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.security.SecurityConfig;

import team.themoment.datagsm.sdk.oauth.exception.BadRequestException;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, WebConfig.class})
@TestPropertySource(properties = "checkup.web.base-url=https://web.test")
class AuthControllerTest {

    private static final String DATAGSM_AUTHORIZE_URL = "https://oauth.example/authorize?state=s";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private LoginSessionService loginSessionService;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private ConsentService consentService;

    private final Member member = Member.create(100L, "학생", MemberRole.STUDENT);

    @Test
    void 로그인_시작은_돌아갈_경로를_넘기고_DataGSM으로_302() throws Exception {
        given(authService.createLoginUrl("/admin/qr")).willReturn(DATAGSM_AUTHORIZE_URL);

        mockMvc.perform(get("/api/v1/auth/login").param("redirect", "/admin/qr"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", DATAGSM_AUTHORIZE_URL));
    }

    @Test
    void 돌아갈_경로가_없어도_로그인을_시작한다() throws Exception {
        given(authService.createLoginUrl(isNull())).willReturn(DATAGSM_AUTHORIZE_URL);

        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", DATAGSM_AUTHORIZE_URL));
    }

    @Test
    void 콜백_성공은_세션을_만들고_웹_로그인_완료_화면으로_302() throws Exception {
        given(authService.completeLogin("code", "state")).willReturn(new LoginResult(member, "/login/complete"));

        mockMvc.perform(get("/api/v1/auth/callback").param("code", "code").param("state", "state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://web.test/login/complete"));

        verify(loginSessionService).login(any(), any(), any());
    }

    @Test
    void 콜백_성공은_로그인_시작_때_정한_경로로_돌아간다() throws Exception {
        given(authService.completeLogin("code", "state")).willReturn(new LoginResult(member, "/admin/qr"));

        mockMvc.perform(get("/api/v1/auth/callback").param("code", "code").param("state", "state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://web.test/admin/qr"));
    }

    @Test
    void state가_유효하지_않으면_웹_로그인_화면으로_오류_코드와_302() throws Exception {
        given(authService.completeLogin("code", "state")).willThrow(new CustomException(ErrorCode.INVALID_OAUTH_STATE));

        mockMvc.perform(get("/api/v1/auth/callback").param("code", "code").param("state", "state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://web.test/login?error=INVALID_OAUTH_STATE"));

        verify(loginSessionService, never()).login(any(), any(), any());
    }

    @Test
    void 이용_권한이_없는_계정도_웹_로그인_화면으로_302() throws Exception {
        given(authService.completeLogin("code", "state")).willThrow(new CustomException(ErrorCode.UNSUPPORTED_ACCOUNT));

        mockMvc.perform(get("/api/v1/auth/callback").param("code", "code").param("state", "state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://web.test/login?error=UNSUPPORTED_ACCOUNT"));
    }

    @Test
    void DataGSM_오류는_전역_예외_처리와_같은_코드로_302() throws Exception {
        given(authService.completeLogin("code", "state")).willThrow(mock(BadRequestException.class));

        mockMvc.perform(get("/api/v1/auth/callback").param("code", "code").param("state", "state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://web.test/login?error=DATAGSM_INVALID_CODE"));
    }

    @Test
    void DataGSM에서_로그인을_취소해_code가_없으면_INVALID_REQUEST로_302() throws Exception {
        mockMvc.perform(get("/api/v1/auth/callback").param("error", "access_denied").param("state", "state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://web.test/login?error=INVALID_REQUEST"));

        verify(authService, never()).completeLogin(any(), any());
    }

    @Test
    void 현재_회원은_이름_역할과_필수_동의_여부를_준다() throws Exception {
        given(memberService.getById(7L)).willReturn(member);
        given(consentService.hasRequiredConsent(7L)).willReturn(true);

        mockMvc.perform(get("/api/v1/auth/me")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(7L, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("학생"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.consented").value(true));
    }

    @Test
    void 동의하지_않은_회원은_consented가_false다() throws Exception {
        given(memberService.getById(7L)).willReturn(member);
        given(consentService.hasRequiredConsent(7L)).willReturn(false);

        mockMvc.perform(get("/api/v1/auth/me")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(7L, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consented").value(false));
    }
}
