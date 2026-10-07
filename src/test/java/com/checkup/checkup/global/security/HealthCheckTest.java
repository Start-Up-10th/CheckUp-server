package com.checkup.checkup.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 상태 확인 API가 로그인 없이 열리고 필수 상태가 내려가면 503을 주는지 검증한다(REQ-OPS-001~002).
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthCheckTest {

    private static final String HEALTH = "/actuator/health";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AtomicBoolean probeUp;

    @AfterEach
    void restore() {
        probeUp.set(true);
    }

    @Test
    @DisplayName("로그인 없이 호출해도 정상이면 200과 UP을 준다")
    void healthIsOpenWithoutLogin() throws Exception {
        mockMvc.perform(get(HEALTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("응답에 DB·Redis 같은 구성 요소와 상세 정보를 넣지 않는다")
    void healthHidesComponentsAndDetails() throws Exception {
        mockMvc.perform(get(HEALTH))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    @DisplayName("필수 상태가 내려가면 503과 DOWN을 주고 복구되면 다시 200이다")
    void healthIsDownWhenIndicatorIsDown() throws Exception {
        probeUp.set(false);
        mockMvc.perform(get(HEALTH))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));

        probeUp.set(true);
        mockMvc.perform(get(HEALTH))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("다른 actuator 경로는 로그인 없이 열리지 않는다")
    void otherActuatorPathsStayClosed() throws Exception {
        mockMvc.perform(get("/actuator"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

    /** 장애 상황을 흉내 내려고 켜고 끌 수 있는 상태 표시를 더한다. */
    @TestConfiguration
    static class ProbeConfig {

        @Bean
        AtomicBoolean probeUp() {
            return new AtomicBoolean(true);
        }

        @Bean
        HealthIndicator probeHealthIndicator(AtomicBoolean probeUp) {
            return () -> probeUp.get() ? Health.up().build() : Health.down().build();
        }
    }
}
