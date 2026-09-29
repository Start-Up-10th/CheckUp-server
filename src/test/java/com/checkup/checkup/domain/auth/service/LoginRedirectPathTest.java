package com.checkup.checkup.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LoginRedirectPathTest {

    @ParameterizedTest
    @ValueSource(strings = {"/main", "/admin", "/login/complete", "/qr?x=1", "/a/b/c"})
    void 같은_웹_안의_상대_경로는_그대로_쓴다(String path) {
        assertThat(LoginRedirectPath.sanitize(path)).isEqualTo(path);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 값이_없으면_로그인_완료_화면이다(String path) {
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
    void 외부로_나갈_수_있는_값은_로그인_완료_화면으로_바꾼다(String path) {
        assertThat(LoginRedirectPath.sanitize(path)).isEqualTo(LoginRedirectPath.DEFAULT);
    }

    @ParameterizedTest
    @ValueSource(ints = {513, 2000})
    void 너무_긴_경로는_로그인_완료_화면이다(int length) {
        assertThat(LoginRedirectPath.sanitize("/" + "a".repeat(length - 1))).isEqualTo(LoginRedirectPath.DEFAULT);
    }
}
