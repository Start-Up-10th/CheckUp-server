package com.checkup.checkup.global.config;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 로그인 없이 API 문서를 볼 수 있고, SESSION 쿠키 인증·공통 오류 응답이 문서에 들어가며
 * 로그인·콜백은 인증 없음으로, 웹훅은 숨김으로 표시되는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("로그인하지 않아도 API 문서와 Swagger UI를 볼 수 있다")
    void docsArePublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SESSION 쿠키 인증과 공통 오류 응답 스키마가 문서에 있다")
    void sessionCookieAndErrorResponseAreDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.securitySchemes.SESSION.in").value("cookie"))
                .andExpect(jsonPath("$.components.securitySchemes.SESSION.name").value("SESSION"))
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.code").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/me'].get.responses.default").exists());
    }

    @Test
    @DisplayName("로그인·콜백은 인증 없음으로 표시하고 웹훅은 문서에서 숨긴다")
    void publicEndpointsAndHiddenWebhook() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].get.security").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/callback'].get.security").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/auth/callback'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/me']").exists())
                .andExpect(jsonPath("$.paths", not(hasKey("/api/v1/webhook"))));
    }
}
