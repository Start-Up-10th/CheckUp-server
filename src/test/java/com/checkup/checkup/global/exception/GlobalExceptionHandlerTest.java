package com.checkup.checkup.global.exception;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.checkup.checkup.domain.face.ai.AiFaceException;

import com.checkup.checkup.global.security.SecurityConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
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
@WebMvcTest(controllers = {GlobalExceptionHandlerTest.ThrowingController.class, GlobalExceptionHandlerTest.BodyController.class})
@Import({SecurityConfig.class, GlobalExceptionHandlerTest.ThrowingController.class, GlobalExceptionHandlerTest.BodyController.class})
class GlobalExceptionHandlerTest {

    private static final String THROW = "/test/throw";
    private static final String PARAMS = "/test/params";
    private static final String BODY = "/test/body";
    private static final String UPSTREAM_MESSAGE = "upstream-body-must-not-leak";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExceptionSource exceptionSource;

    @Test
    @DisplayName("얼굴 AI의 401·422·503은 사용자 로그인 오류와 분리한다")
    void faceAiErrorsAreSeparateFromUserAuthErrors() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var unauthorized = handler.handleFaceAi(new AiFaceException(401, "frame", "unauthorized"));
        var rejected = handler.handleFaceAi(new AiFaceException(422, "enrollment", "low_quality"));
        var unavailable = handler.handleFaceAi(new AiFaceException(503, "frame", "not_ready"));
        var multipleFaces = handler.handleFaceAi(new AiFaceException(422, "enrollment", "MULTIPLE_IDENTITIES"));
        var lowLight = handler.handleFaceAi(new AiFaceException(422, "enrollment", "LOW_LIGHT"));
        var modelMismatch = handler.handleFaceAi(new AiFaceException(422, "session_create", "MODEL_MISMATCH"));

        assertThat(unauthorized.getStatusCode().value()).isEqualTo(503);
        assertThat(unauthorized.getBody().code()).isEqualTo("FACE_AI_UNAVAILABLE");
        assertThat(rejected.getStatusCode().value()).isEqualTo(422);
        assertThat(rejected.getBody().code()).isEqualTo("FACE_ENROLLMENT_REJECTED");
        assertThat(unavailable.getStatusCode().value()).isEqualTo(503);
        assertThat(unavailable.getBody().code()).isEqualTo("FACE_AI_UNAVAILABLE");
        assertThat(multipleFaces.getBody().code()).isEqualTo("FACE_ENROLLMENT_MULTIPLE_IDENTITIES");
        assertThat(lowLight.getStatusCode().value()).isEqualTo(422);
        assertThat(lowLight.getBody().code()).isEqualTo("FACE_ENROLLMENT_LOW_LIGHT");
        assertThat(modelMismatch.getStatusCode().value()).isEqualTo(502);
        assertThat(modelMismatch.getBody().code()).isEqualTo("FACE_AI_BAD_GATEWAY");
    }

    @Test
    @DisplayName("code·state가 모두 없으면 400과 필드별 오류를 모두 반환한다")
    void missingParamsReturnsAllFieldErrors() throws Exception {
        mockMvc.perform(get(PARAMS).with(user("admin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("code", "state")));
    }

    @Test
    @DisplayName("서비스 예외는 ErrorCode의 상태·코드·메시지로 응답하고 errors를 생략한다")
    void customExceptionUsesErrorCode() throws Exception {
        willThrow(new CustomException(ErrorCode.INVALID_OAUTH_STATE)).given(exceptionSource).run();

        mockMvc.perform(get(THROW).with(user("admin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OAUTH_STATE"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_OAUTH_STATE.getMessage()))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("DataGSM 잘못된 요청은 400 DATAGSM_INVALID_CODE로 응답하고 SDK 메시지를 노출하지 않는다")
    void dataGsmBadRequestIsInvalidCode() throws Exception {
        willThrow(new BadRequestException(UPSTREAM_MESSAGE)).given(exceptionSource).run();

        mockMvc.perform(get(THROW).with(user("admin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATAGSM_INVALID_CODE"))
                .andExpect(content().string(not(containsString(UPSTREAM_MESSAGE))));
    }

    @Test
    @DisplayName("DataGSM 서버 오류는 503 DATAGSM_UNAVAILABLE로 응답한다")
    void dataGsmServerErrorIsUnavailable() throws Exception {
        willThrow(new ServerErrorException(UPSTREAM_MESSAGE)).given(exceptionSource).run();

        mockMvc.perform(get(THROW).with(user("admin")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DATAGSM_UNAVAILABLE"))
                .andExpect(content().string(not(containsString(UPSTREAM_MESSAGE))));
    }

    @Test
    @DisplayName("DataGSM 인증 실패는 서버 설정 문제라 401이 아닌 502 DATAGSM_ERROR로 응답한다")
    void dataGsmUnauthorizedIsBadGateway() throws Exception {
        willThrow(new UnauthorizedException(UPSTREAM_MESSAGE)).given(exceptionSource).run();

        mockMvc.perform(get(THROW).with(user("admin")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("DATAGSM_ERROR"))
                .andExpect(content().string(not(containsString(UPSTREAM_MESSAGE))));
    }

    @Test
    @DisplayName("교체 전 ResponseStatusException은 500이 아니라 원래 상태 코드로 응답하고 사유를 노출하지 않는다")
    void responseStatusExceptionKeepsStatus() throws Exception {
        willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, UPSTREAM_MESSAGE)).given(exceptionSource).run();

        mockMvc.perform(get(THROW).with(user("admin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(content().string(not(containsString(UPSTREAM_MESSAGE))));
    }

    @Test
    @DisplayName("예상하지 못한 예외는 500으로 응답하고 예외 메시지를 노출하지 않는다")
    void unexpectedExceptionIsInternalError() throws Exception {
        willThrow(new IllegalStateException(UPSTREAM_MESSAGE)).given(exceptionSource).run();

        mockMvc.perform(get(THROW).with(user("admin")))
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

    /** 테스트 전용 컨트롤러가 던질 예외를 정한다. */
    interface ExceptionSource {
        void run();
    }

    record ParamsRequest(@NotBlank String code, @NotBlank String state) {}

    /** 서비스 예외·SDK 예외·요청 값 검증 실패를 재현하는 테스트 전용 컨트롤러. */
    @RestController
    static class ThrowingController {

        private final ExceptionSource exceptionSource;

        ThrowingController(ExceptionSource exceptionSource) {
            this.exceptionSource = exceptionSource;
        }

        @GetMapping(THROW)
        void throwConfigured() {
            exceptionSource.run();
        }

        @GetMapping(PARAMS)
        void params(@Valid ParamsRequest request) {
        }
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
