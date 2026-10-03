package com.checkup.checkup.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인하지 않은 요청이 401로 막혀도 세션을 만들지 않는지 검증한다.
 */
@WebMvcTest(controllers = AnonymousSessionTest.ProbeController.class)
@Import({SecurityConfig.class, AnonymousSessionTest.ProbeController.class})
class AnonymousSessionTest {

    private static final String PROTECTED = "/test/protected";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("로그인하지 않은 요청이 401로 막혀도 세션을 만들지 않는다")
    void unauthenticatedRequestDoesNotCreateSession() throws Exception {
        MvcResult result = mockMvc.perform(get(PROTECTED))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    @DisplayName("로그인한 요청은 그대로 처리된다")
    void authenticatedRequestIsHandled() throws Exception {
        mockMvc.perform(get(PROTECTED)
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(1L, null, List.of()))))
                .andExpect(status().isOk());
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
