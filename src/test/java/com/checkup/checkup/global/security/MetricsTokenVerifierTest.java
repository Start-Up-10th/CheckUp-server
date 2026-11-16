package com.checkup.checkup.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 지표 토큰이 비어 있으면 시작하지 못하고, 정확한 Bearer 토큰만 통과하는지 검증한다.
 */
class MetricsTokenVerifierTest {

    private final MetricsTokenVerifier verifier = new MetricsTokenVerifier("secret-token");

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("토큰이 비어 있으면 만들 수 없다")
    void rejectsBlankToken(String token) {
        assertThatThrownBy(() -> new MetricsTokenVerifier(token))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("같은 Bearer 토큰은 통과한다")
    void acceptsSameToken() {
        assertThat(verifier.matches(requestWith("Bearer secret-token"))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer wrong-token", "Bearer secret-token2", "Bearer ", "secret-token", "Basic secret-token"})
    @DisplayName("다른 토큰이나 다른 형식은 통과하지 못한다")
    void rejectsOtherValues(String header) {
        assertThat(verifier.matches(requestWith(header))).isFalse();
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 통과하지 못한다")
    void rejectsMissingHeader() {
        assertThat(verifier.matches(new MockHttpServletRequest())).isFalse();
    }

    private MockHttpServletRequest requestWith(String header) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", header);
        return request;
    }
}
