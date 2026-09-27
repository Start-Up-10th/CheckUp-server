package com.checkup.checkup.global.exception;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkup.checkup.domain.auth.controller.AuthController;
import com.checkup.checkup.domain.auth.service.AuthService;
import com.checkup.checkup.domain.auth.service.LoginSessionService;
import com.checkup.checkup.domain.member.service.MemberService;
import com.checkup.checkup.global.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import team.themoment.datagsm.sdk.oauth.exception.BadRequestException;
import team.themoment.datagsm.sdk.oauth.exception.ServerErrorException;
import team.themoment.datagsm.sdk.oauth.exception.UnauthorizedException;

/**
 * 검증 실패·서비스 예외·DataGSM SDK 예외가 공통 오류 응답으로 변환되고,
 * 응답 본문에 예외 메시지가 노출되지 않는지 검증한다.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandlerTest.BodyController.class})
class GlobalExceptionHandlerTest {

    private static final String CALLBACK = "/api/v1/auth/callback";
    private static final String BODY = "/test/body";
    private static final String UPSTREAM_MESSAGE = "upstream-body-must-not-leak";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private LoginSessionService loginSessionService;

    @MockitoBean
    private MemberService memberService;

    @Test
    @DisplayName("code·state가 모두 없으면 400과 필드별 오류를 모두 반환한다")
    void missingParamsReturnsAllFieldErrors() throws Exception {
        mockMvc.perform(get(CALLBACK))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("code", "state")));
    }

    @Test
    @DisplayName("서비스 예외는 ErrorCode의 상태·코드·메시지로 응답하고 errors를 생략한다")
    void customExceptionUsesErrorCode() throws Exception {
        given(authService.completeLogin(anyString(), anyString()))
                .willThrow(new CustomException(ErrorCode.INVALID_OAUTH_STATE));

        mockMvc.perform(get(CALLBACK).param("code", "c").param("state", "s"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OAUTH_STATE"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_OAUTH_STATE.getMessage()))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("DataGSM 잘못된 요청은 400 DATAGSM_INVALID_CODE로 응답하고 SDK 메시지를 노출하지 않는다")
    void dataGsmBadRequestIsInvalidCode() throws Exception {
        given(authService.completeLogin(anyString(), anyString()))
                .willThrow(new BadRequestException(UPSTREAM_MESSAGE));

        mockMvc.perform(get(CALLBACK).param("code", "c").param("state", "s"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATAGSM_INVALID_CODE"))
                .andExpect(content().string(not(containsString(UPSTREAM_MESSAGE))));
    }

    @Test
    @DisplayName("DataGSM 서버 오류는 503 DATAGSM_UNAVAILABLE로 응답한다")
    void dataGsmServerErrorIsUnavailable() throws Exception {
        given(authService.completeLogin(anyString(), anyString()))
                .willThrow(new ServerErrorException(UPSTREAM_MESSAGE));

        mockMvc.perform(get(CALLBACK).param("code", "c").param("state", "s"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DATAGSM_UNAVAILABLE"))
                .andExpect(content().string(not(containsString(UPSTREAM_MESSAGE))));
    }

    @Test
    @DisplayName("DataGSM 인증 실패는 서버 설정 문제라 401이 아닌 502 DATAGSM_ERROR로 응답한다")
    void dataGsmUnauthorizedIsBadGateway() throws Exception {
        given(authService.completeLogin(anyString(), anyString()))
                .willThrow(new UnauthorizedException(UPSTREAM_MESSAGE));

        mockMvc.perform(get(CALLBACK).param("code", "c").param("state", "s"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("DATAGSM_ERROR"))
                .andExpect(content().string(not(containsString(UPSTREAM_MESSAGE))));
    }

    @Test
    @DisplayName("교체 전 ResponseStatusException은 500이 아니라 원래 상태 코드로 응답하고 사유를 노출하지 않는다")
    void responseStatusExceptionKeepsStatus() throws Exception {
        given(authService.completeLogin(anyString(), anyString()))
                .willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, UPSTREAM_MESSAGE));

        mockMvc.perform(get(CALLBACK).param("code", "c").param("state", "s"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(content().string(not(containsString(UPSTREAM_MESSAGE))));
    }

    @Test
    @DisplayName("예상하지 못한 예외는 500으로 응답하고 예외 메시지를 노출하지 않는다")
    void unexpectedExceptionIsInternalError() throws Exception {
        given(authService.completeLogin(anyString(), anyString()))
                .willThrow(new IllegalStateException(UPSTREAM_MESSAGE));

        mockMvc.perform(get(CALLBACK).param("code", "c").param("state", "s"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(content().string(not(containsString(UPSTREAM_MESSAGE))));
    }

    @Test
    @DisplayName("읽을 수 없는 body(enum에 없는 값)는 500이 아니라 400 INVALID_REQUEST로 응답한다")
    void unreadableBodyIsBadRequest() throws Exception {
        mockMvc.perform(post(BODY).with(user("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"GYM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("Spring MVC 기본 4xx 예외(지원하지 않는 Content-Type)는 원래 상태 코드 415를 유지한다")
    void unsupportedMediaTypeKeepsStatus() throws Exception {
        mockMvc.perform(post(BODY).with(user("admin"))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("purpose=DORMITORY"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    enum Purpose { DORMITORY }

    record BodyRequest(Purpose purpose) {}

    /** JSON body를 받는 API가 아직 없어 body 관련 예외를 재현하는 테스트 전용 컨트롤러. */
    @RestController
    static class BodyController {

        @PostMapping(BODY)
        void accept(@RequestBody BodyRequest request) {
        }
    }
}
