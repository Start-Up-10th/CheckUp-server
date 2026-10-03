package com.checkup.checkup.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 로그인 뒤 돌아갈 경로가 같은 웹 안의 상대 경로일 때만 쓰이고, 외부로 나갈 수 있거나 비정상인 값은 로그인 완료 화면으로 바뀌는지 검증한다.
 */
class LoginRedirectPathTest {

    @ParameterizedTest
    @ValueSource(strings = {"/main", "/admin", "/login/complete", "/qr?x=1", "/a/b/c"})
    @DisplayName("같은 웹 안의 상대 경로는 그대로 쓴다")
    void relativePathInWebIsKept(String path) {
        assertThat(LoginRedirectPath.sanitize(path)).isEqualTo(path);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("값이 없으면 로그인 완료 화면이다")
    void missingValueUsesDefault(String path) {
        assertThat(LoginRedirectPath.sanitize(path)).isEqualTo(LoginRedirectPath.DEFAULT);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.example",
            "http://evil.example/main",
            "//evil.example",
            "//evil.example/main",
            "/\\evil.example",
            "\\\\evil.example",
            "javascript:alert(1)",
            "main",
            "/main\r\nSet-Cookie:x=1",
            "/ma in",
            "/main\t"
    })
    @DisplayName("외부로 나갈 수 있는 값은 로그인 완료 화면으로 바꾼다")
    void externalPathUsesDefault(String path) {
        assertThat(LoginRedirectPath.sanitize(path)).isEqualTo(LoginRedirectPath.DEFAULT);
    }

    @ParameterizedTest
    @ValueSource(ints = {513, 2000})
    @DisplayName("너무 긴 경로는 로그인 완료 화면이다")
    void tooLongPathUsesDefault(int length) {
        assertThat(LoginRedirectPath.sanitize("/" + "a".repeat(length - 1))).isEqualTo(LoginRedirectPath.DEFAULT);
    }
}
