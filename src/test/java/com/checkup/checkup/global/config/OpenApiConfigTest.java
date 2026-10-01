package com.checkup.checkup.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 로그인 없이 API 문서를 볼 수 있고, SESSION 쿠키 인증·공통 오류 응답이 문서에 들어가며
 * 로그인·콜백은 인증 없음으로, 웹훅은 숨김으로 표시되는지 검증한다.
 * 모든 API에 한글 묶음 이름과 요약이 있고, 로그아웃이 문서에 있으며, 세션 회원 id는 요청 값으로 드러나지 않는지도 검증한다.
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

    @Test
    @DisplayName("모든 API에 요약과 한글 묶음 이름이 있고 컨트롤러 클래스 이름이 묶음으로 나오지 않는다")
    void everyOperationHasSummaryAndTag() throws Exception {
        String docs = apiDocs();

        List<String> operationIds = JsonPath.read(docs, "$.paths.*.*.operationId");
        List<String> summaries = JsonPath.read(docs, "$.paths.*.*.summary");
        List<String> tags = JsonPath.read(docs, "$.paths.*.*.tags[*]");

        assertThat(operationIds).isNotEmpty();
        assertThat(summaries).hasSameSizeAs(operationIds).allSatisfy(summary -> assertThat(summary).isNotBlank());
        assertThat(tags).isNotEmpty().noneMatch(tag -> tag.endsWith("-controller"));
        assertThat(tags).contains("인증", "봉사 관리", "알림", "QR(관리자)");
    }

    @Test
    @DisplayName("Spring Security가 처리하는 로그아웃도 204 응답으로 문서에 있다")
    void logoutIsDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.summary").value("로그아웃"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.tags[0]").value("인증"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.responses['204']").exists());
    }

    private String apiDocs() throws Exception {
        return mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
    }
}
