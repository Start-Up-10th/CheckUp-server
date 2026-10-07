package com.checkup.checkup.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.checkup.checkup.domain.auth.dto.response.CurrentStudentResponse;
import com.checkup.checkup.domain.auth.dto.response.OAuthLoginResponse;
import com.checkup.checkup.domain.auth.service.AuthService;
import com.checkup.checkup.domain.auth.service.CurrentMemberService;
import com.checkup.checkup.domain.auth.service.LoginResult;
import com.checkup.checkup.domain.auth.service.LoginSessionService;
import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.global.config.WebConfig;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.security.SecurityConfig;

import team.themoment.datagsm.sdk.oauth.exception.BadRequestException;

/**
 * 인증 API의 로그인 시작·콜백 리다이렉트(성공·실패 모두 웹으로 302)와 현재 회원 조회 응답·401을 검증한다(REQ-AUTH-001·004).
 */
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
    private CurrentMemberService currentMemberService;

    private final Member member = Member.create(100L, "학생", MemberRole.STUDENT);

    @Test
    @DisplayName("로그인 시작은 돌아갈 경로를 넘기고 DataGSM으로 302")
    void loginRedirectsToDataGsmWithRedirectPath() throws Exception {
        given(authService.createLoginUrl("/admin/qr")).willReturn(DATAGSM_AUTHORIZE_URL);

        mockMvc.perform(get("/api/v1/auth/login").param("redirect", "/admin/qr"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", DATAGSM_AUTHORIZE_URL));
    }

    @Test
    @DisplayName("돌아갈 경로가 없어도 로그인을 시작한다")
    void loginStartsWithoutRedirectPath() throws Exception {
        given(authService.createLoginUrl(isNull())).willReturn(DATAGSM_AUTHORIZE_URL);

        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", DATAGSM_AUTHORIZE_URL));
    }

    @Test
    @DisplayName("콜백 성공은 세션을 만들고 웹 로그인 완료 화면으로 302")
    void callbackSuccessCreatesSessionAndRedirectsToComplete() throws Exception {
        given(authService.completeLogin("code", "state")).willReturn(new LoginResult(member, "/login/complete"));

        mockMvc.perform(get("/api/v1/auth/callback").param("code", "code").param("state", "state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://web.test/login/complete"));

        verify(loginSessionService).login(any(), any(), any());
    }

    @Test
    @DisplayName("콜백 성공은 로그인 시작 때 정한 경로로 돌아간다")
    void callbackSuccessReturnsToSavedPath() throws Exception {
        given(authService.completeLogin("code", "state")).willReturn(new LoginResult(member, "/admin/qr"));

        mockMvc.perform(get("/api/v1/auth/callback").param("code", "code").param("state", "state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://web.test/admin/qr"));
    }

    @Test
    @DisplayName("state가 유효하지 않으면 웹 로그인 화면으로 오류 코드와 302")
    void invalidStateRedirectsToLoginWithErrorCode() throws Exception {
        given(authService.completeLogin("code", "state")).willThrow(new CustomException(ErrorCode.INVALID_OAUTH_STATE));

        mockMvc.perform(get("/api/v1/auth/callback").param("code", "code").param("state", "state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://web.test/login?error=INVALID_OAUTH_STATE"));

        verify(loginSessionService, never()).login(any(), any(), any());
    }

    @Test
    @DisplayName("이용 권한이 없는 계정도 웹 로그인 화면으로 302")
    void unsupportedAccountRedirectsToLogin() throws Exception {
        given(authService.completeLogin("code", "state")).willThrow(new CustomException(ErrorCode.UNSUPPORTED_ACCOUNT));

        mockMvc.perform(get("/api/v1/auth/callback").param("code", "code").param("state", "state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://web.test/login?error=UNSUPPORTED_ACCOUNT"));
    }

    @Test
    @DisplayName("DataGSM 오류는 전역 예외 처리와 같은 코드로 302")
    void dataGsmErrorRedirectsWithSameCodeAsGlobalHandler() throws Exception {
        given(authService.completeLogin("code", "state")).willThrow(mock(BadRequestException.class));

        mockMvc.perform(get("/api/v1/auth/callback").param("code", "code").param("state", "state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://web.test/login?error=DATAGSM_INVALID_CODE"));
    }

    @Test
    @DisplayName("로그인 처리 중 예상하지 못한 오류도 웹 로그인 화면으로 INTERNAL_SERVER_ERROR와 302")
    void unexpectedErrorRedirectsToLogin() throws Exception {
        given(authService.completeLogin("code", "state")).willThrow(new IllegalStateException("redis down"));

        mockMvc.perform(get("/api/v1/auth/callback").param("code", "code").param("state", "state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://web.test/login?error=INTERNAL_SERVER_ERROR"));

        verify(loginSessionService, never()).login(any(), any(), any());
    }

    @Test
    @DisplayName("세션을 만들다 예상하지 못한 오류가 나도 웹 로그인 화면으로 302")
    void sessionFailureRedirectsToLogin() throws Exception {
        given(authService.completeLogin("code", "state")).willReturn(new LoginResult(member, "/login/complete"));
        willThrow(new IllegalStateException("session failed")).given(loginSessionService).login(any(), any(), any());

        mockMvc.perform(get("/api/v1/auth/callback").param("code", "code").param("state", "state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://web.test/login?error=INTERNAL_SERVER_ERROR"));
    }

    @Test
    @DisplayName("DataGSM에서 로그인을 취소해 code가 없으면 INVALID_REQUEST로 302")
    void missingCodeRedirectsWithInvalidRequest() throws Exception {
        mockMvc.perform(get("/api/v1/auth/callback").param("error", "access_denied").param("state", "state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://web.test/login?error=INVALID_REQUEST"));

        verify(authService, never()).completeLogin(any(), any());
    }

    @Test
    @DisplayName("현재 회원은 이름 역할 필수 동의 여부와 학생 정보를 준다")
    void meReturnsNameRoleConsentAndStudent() throws Exception {
        given(currentMemberService.getCurrentMember(7L)).willReturn(new OAuthLoginResponse(
                "학생", MemberRole.STUDENT, true,
                new CurrentStudentResponse(1234L, 2, 4, 5, 2405, 412, 4)));

        mockMvc.perform(get("/api/v1/auth/me")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(7L, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("학생"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.consented").value(true))
                .andExpect(jsonPath("$.student.studentId").value(1234))
                .andExpect(jsonPath("$.student.grade").value(2))
                .andExpect(jsonPath("$.student.classNumber").value(4))
                .andExpect(jsonPath("$.student.number").value(5))
                .andExpect(jsonPath("$.student.studentNumber").value(2405))
                .andExpect(jsonPath("$.student.dormitoryRoom").value(412))
                .andExpect(jsonPath("$.student.dormitoryFloor").value(4));
    }

    @Test
    @DisplayName("학생 정보가 없는 회원은 student가 null이다")
    void meReturnsNullStudentForNonStudent() throws Exception {
        given(currentMemberService.getCurrentMember(7L))
                .willReturn(new OAuthLoginResponse("교사", MemberRole.ADMIN, false, null));

        mockMvc.perform(get("/api/v1/auth/me")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(7L, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consented").value(false))
                .andExpect(jsonPath("$.student").isEmpty());
    }

    @Test
    @DisplayName("로그인하지 않으면 현재 회원은 401")
    void meWithoutLoginReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());

        verify(currentMemberService, never()).getCurrentMember(any());
    }
}
