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
 * 지표를 켜면 토큰을 가진 Prometheus만 지표를 읽을 수 있고 다른 actuator 경로는 그대로 닫혀 있는지 검증한다.
 */
@SpringBootTest(properties = {"checkup.metrics.enabled=true", "checkup.metrics.token=test-metrics-token"})
@AutoConfigureMockMvc
class MetricsEndpointEnabledTest {

    private static final String VALID_AUTHORIZATION = "Bearer test-metrics-token";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("토큰이 맞으면 DB 연결 풀·JVM·Hibernate 지표를 읽을 수 있다")
    void prometheusIsOpenWithToken() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", VALID_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("hikaricp_connections_active")))
                .andExpect(content().string(containsString("jvm_memory_used_bytes")))
                .andExpect(content().string(containsString("hibernate_query_executions_total")));
    }

    @Test
    @DisplayName("토큰이 없거나 틀리면 지표를 읽을 수 없다")
    void prometheusIsClosedWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer wrong-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("지표 토큰으로 다른 actuator 경로는 열리지 않는다")
    void otherActuatorPathsStayClosed() throws Exception {
        for (String path : new String[] {"/actuator", "/actuator/env", "/actuator/metrics"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get(path).header("Authorization", VALID_AUTHORIZATION))
                    .andExpect(status().isUnauthorized());
        }
    }
}
