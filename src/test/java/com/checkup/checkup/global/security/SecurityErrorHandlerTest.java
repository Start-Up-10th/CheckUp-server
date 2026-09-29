package com.checkup.checkup.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = SecurityErrorHandlerTest.ProbeController.class)
@Import({SecurityConfig.class, SecurityErrorHandlerTest.ProbeController.class})
class SecurityErrorHandlerTest {

    private static final String PROTECTED = "/test/protected";
    private static final String SECRET_MESSAGE = "secret-detail-must-not-leak";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 로그인하지_않은_요청은_401과_공통_오류_형식으로_응답한다() throws Exception {
        mockMvc.perform(get(PROTECTED))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void 권한이_없는_요청은_403과_공통_오류_형식으로_응답하고_예외_메시지를_넣지_않는다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new SecurityErrorHandler(objectMapper)
                .handle(new MockHttpServletRequest(), response, new AccessDeniedException(SECRET_MESSAGE));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString())
                .isEqualTo("{\"code\":\"FORBIDDEN\",\"message\":\"접근 권한이 없습니다.\"}")
                .doesNotContain(SECRET_MESSAGE);
    }

    /** 로그인이 필요한 API를 흉내 내는 테스트 전용 컨트롤러. */
    @RestController
    static class ProbeController {

        @GetMapping(PROTECTED)
        String protectedEndpoint() {
            return "ok";
        }
    }
}
