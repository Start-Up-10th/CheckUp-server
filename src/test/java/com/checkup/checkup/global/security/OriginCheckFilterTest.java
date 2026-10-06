package com.checkup.checkup.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.ObjectMapper;

/**
 * 상태를 바꾸는 요청이 웹 주소와 같은 출처일 때만 통과하고, 다른 출처면 403 INVALID_ORIGIN으로 막히는지 검증한다(#146).
 */
class OriginCheckFilterTest {

    private static final String WEB = "https://checkup.gsmsv.site";
    private static final String OTHER = "https://other.gsmsv.site";
    private static final String API = "/api/v1/volunteer/1/duty/complete";

    private final OriginCheckFilter filter =
            new OriginCheckFilter(WEB + "/", new SecurityErrorHandler(new ObjectMapper()));

    @Test
    @DisplayName("같은 Origin의 POST는 통과한다")
    void sameOriginPasses() throws Exception {
        MockHttpServletRequest request = post(API);
        request.addHeader("Origin", WEB);

        assertPassed(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTTPS://CHECKUP.GSMSV.SITE", "https://checkup.gsmsv.site:443"})
    @DisplayName("대소문자와 기본 포트 표기가 달라도 같은 출처면 통과한다")
    void equivalentOriginPasses(String origin) throws Exception {
        MockHttpServletRequest request = post(API);
        request.addHeader("Origin", origin);

        assertPassed(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {OTHER, "https://checkup.gsmsv.site:8443", "http://checkup.gsmsv.site", "null", "%%%"})
    @DisplayName("다른 서브도메인·포트·scheme이거나 null·해석 불가 Origin의 POST는 403이다")
    void otherOriginIsRejected(String origin) throws Exception {
        MockHttpServletRequest request = post(API);
        request.addHeader("Origin", origin);

        assertRejected(request);
    }

    @Test
    @DisplayName("Origin이 없으면 Referer의 출처로 판정한다")
    void refererIsUsedWithoutOrigin() throws Exception {
        MockHttpServletRequest same = post(API);
        same.addHeader("Referer", WEB + "/admin/volunteer?tab=duty");
        MockHttpServletRequest other = post(API);
        other.addHeader("Referer", OTHER + "/attack");

        assertPassed(same);
        assertRejected(other);
    }

    @Test
    @DisplayName("Origin과 Referer가 모두 없는 요청은 브라우저 밖 요청으로 보고 통과한다")
    void requestWithoutSourceHeadersPasses() throws Exception {
        assertPassed(post(API));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "OPTIONS", "TRACE"})
    @DisplayName("상태를 바꾸지 않는 메서드는 다른 Origin이어도 확인하지 않는다")
    void safeMethodsAreNotChecked(String method) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, API);
        request.addHeader("Origin", OTHER);

        assertPassed(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/webhook", "/api/v1/webhook/sync"})
    @DisplayName("DataGSM 웹훅은 서명으로 검증하므로 출처를 확인하지 않는다")
    void webhookIsNotChecked(String path) throws Exception {
        MockHttpServletRequest request = post(path);
        request.addHeader("Origin", OTHER);

        assertPassed(request);
    }

    @Test
    @DisplayName("웹훅과 접두사만 같은 경로는 확인한다")
    void webhookPrefixLookalikeIsChecked() throws Exception {
        MockHttpServletRequest request = post("/api/v1/webhooks");
        request.addHeader("Origin", OTHER);

        assertRejected(request);
    }

    private static MockHttpServletRequest post(String path) {
        return new MockHttpServletRequest("POST", path);
    }

    private void assertPassed(MockHttpServletRequest request) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private void assertRejected(MockHttpServletRequest request) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"INVALID_ORIGIN\"");
    }
}
