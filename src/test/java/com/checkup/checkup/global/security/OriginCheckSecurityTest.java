package com.checkup.checkup.global.security;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보안 필터 체인에서 다른 출처의 상태 변경 요청이 로그인 세션이 있어도 403으로 막히는지 검증한다(#146).
 */
@WebMvcTest(controllers = OriginCheckSecurityTest.ProbeController.class)
@Import({SecurityConfig.class, OriginCheckSecurityTest.ProbeController.class})
@TestPropertySource(properties = "checkup.web.base-url=https://checkup.gsmsv.site")
class OriginCheckSecurityTest {

    private static final String WEB = "https://checkup.gsmsv.site";
    private static final String OTHER = "https://other.gsmsv.site";
    private static final String ACTION = "/test/action";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("로그인한 관리자 세션이어도 다른 출처의 POST는 403 INVALID_ORIGIN이다")
    void otherOriginIsRejectedEvenWhenLoggedIn() throws Exception {
        mockMvc.perform(post(ACTION).header("Origin", OTHER).with(loggedIn()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_ORIGIN"));
    }

    @Test
    @DisplayName("같은 출처의 로그인한 POST는 처리된다")
    void sameOriginIsHandled() throws Exception {
        mockMvc.perform(post(ACTION).header("Origin", WEB).with(loggedIn()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("다른 출처의 로그아웃 요청도 막힌다")
    void logoutFromOtherOriginIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").header("Origin", OTHER).with(loggedIn()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_ORIGIN"));
    }

    private static RequestPostProcessor loggedIn() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(1L, null, List.of()));
    }

    /** 로그인이 필요한 상태 변경 API를 흉내 내는 테스트 전용 컨트롤러. */
    @RestController
    static class ProbeController {

        @PostMapping(ACTION)
        String action() {
            return "ok";
        }
    }
}
