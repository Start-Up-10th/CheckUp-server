package com.checkup.checkup.global.metrics;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 지표를 켜면 Prometheus가 로그인 없이 지표를 읽을 수 있고 다른 actuator 경로는 그대로 닫혀 있는지 검증한다.
 */
@SpringBootTest(properties = "checkup.metrics.enabled=true")
@AutoConfigureMockMvc
class MetricsEndpointEnabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("로그인 없이 DB 연결 풀·JVM·Hibernate 지표를 읽을 수 있다")
    void prometheusIsOpenWithoutLogin() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("hikaricp_connections_active")))
                .andExpect(content().string(containsString("jvm_memory_used_bytes")))
                .andExpect(content().string(containsString("hibernate_query_executions_total")));
    }

    @Test
    @DisplayName("지표를 켜도 다른 actuator 경로는 로그인 없이 열리지 않는다")
    void otherActuatorPathsStayClosed() throws Exception {
        mockMvc.perform(get("/actuator"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }
}
