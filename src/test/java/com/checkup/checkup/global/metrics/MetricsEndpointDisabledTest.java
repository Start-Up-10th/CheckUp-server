package com.checkup.checkup.global.metrics;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 지표를 켜지 않은 기본 설정에서는 지표 경로가 로그인 없이 열리지 않고 로그인해도 존재하지 않는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MetricsEndpointDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("로그인 없이 호출하면 401이다")
    void prometheusIsClosedWithoutLogin() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그인해도 지표 경로가 없어 404다")
    void prometheusDoesNotExistEvenWithLogin() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(1L, null, List.of()))))
                .andExpect(status().isNotFound());
    }
}
