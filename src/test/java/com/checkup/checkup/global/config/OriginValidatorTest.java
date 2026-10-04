package com.checkup.checkup.global.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.checkup.checkup.domain.qr.config.QrProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 웹 주소 설정(PUBLIC_ORIGIN)이 http(s) origin일 때만 통과하고, 깨진 값이면 설정을 만들 때 실패해 서버가 시작하지 않는지 검증한다(#94).
 */
class OriginValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:3000",
            "https://checkup.example",
            "http://service.gsmsv.site:33951",
            "http://service.gsmsv.site:33951/",
            "HTTPS://checkup.example"
    })
    @DisplayName("http(s) origin과 끝의 / 하나는 통과한다")
    void validOriginPasses(String value) {
        assertThatCode(() -> OriginValidator.requireOrigin(value)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:3000 # 웹",
            "http://localhost:3000\r",
            "\"http://localhost:3000\"",
            "https://<WEB_DOMAIN>",
            "${PUBLIC_ORIGIN}"
    })
    @DisplayName("공백·주석·줄바꿈·따옴표·채우지 않은 자리표시자는 거부한다")
    void malformedValueIsRejected(String value) {
        assertThatThrownBy(() -> OriginValidator.requireOrigin(value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "localhost:3000",
            "http:localhost:3000",
            "ftp://checkup.example",
            "http://checkup.example/app",
            "http://checkup.example?a=1",
            "http://checkup.example#a",
            "http://user:pw@checkup.example"
    })
    @DisplayName("http(s)가 아니거나 호스트가 없거나 경로·쿼리·fragment·사용자 정보가 붙으면 거부한다")
    void nonOriginIsRejected(String value) {
        assertThatThrownBy(() -> OriginValidator.requireOrigin(value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("예외 메시지에 설정값을 넣지 않는다")
    void messageDoesNotContainValue() {
        assertThatThrownBy(() -> OriginValidator.requireOrigin("https://secret-host.example/path"))
                .hasMessageNotContaining("secret-host");
    }

    @Test
    @DisplayName("빈 값 검사는 @NotBlank에 맡기고 null은 통과시킨다")
    void nullIsLeftToNotBlank() {
        assertThatCode(() -> OriginValidator.requireOrigin(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("WebProperties와 QrProperties는 깨진 웹 주소로 만들 수 없다")
    void propertiesRejectMalformedOrigin() {
        assertThatThrownBy(() -> new WebProperties("http://localhost:3000 # 웹"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QrProperties(Duration.ofMinutes(15), Duration.ofSeconds(60),
                Duration.ofSeconds(30), Duration.ofHours(1), "http://localhost:3000/qr"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new WebProperties("http://localhost:3000")).doesNotThrowAnyException();
    }
}
