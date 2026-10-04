package com.checkup.checkup.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.checkup.checkup.global.exception.DataGsmErrorCodes;
import com.checkup.checkup.global.exception.ErrorCode;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import team.themoment.datagsm.sdk.oauth.DataGsmOAuthClient;
import team.themoment.datagsm.sdk.oauth.exception.DataGsmException;

/**
 * DataGSM이 응답하지 않을 때 설정한 대기 시간 안에 요청을 끊고 DATAGSM_UNAVAILABLE로 바꾸는지,
 * 대기 시간 설정이 0 이하이면 클라이언트를 만들 때 실패하는지 검증한다(#110).
 */
class DataGsmConfigTest {

    private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(300);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofMillis(300);

    private final DataGsmConfig config = new DataGsmConfig();

    @Test
    @DisplayName("DataGSM이 응답하지 않으면 대기 시간 안에 끊고 DATAGSM_UNAVAILABLE이 된다")
    void silentServerTimesOutAsUnavailable() throws IOException {
        try (ServerSocket silentServer = new ServerSocket(0);
             DataGsmOAuthClient client = DataGsmOAuthClient.builder("client-id", "client-secret")
                     .authorizationBaseUrl("http://localhost:" + silentServer.getLocalPort())
                     .httpClient(DataGsmConfig.oauthHttpClient(CONNECT_TIMEOUT, RESPONSE_TIMEOUT))
                     .build()) {
            long startedAt = System.nanoTime();

            DataGsmException thrown = catchThrowableOfType(
                    DataGsmException.class,
                    () -> client.exchangeCodeForToken("code", "http://localhost/callback", "verifier"));

            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
            assertThat(thrown).isNotNull();
            assertThat(DataGsmErrorCodes.of(thrown)).isEqualTo(ErrorCode.DATAGSM_UNAVAILABLE);
            assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
        }
    }

    @Test
    @DisplayName("대기 시간 설정이 0 이하이면 클라이언트를 만들지 못한다")
    void nonPositiveTimeoutIsRejected() {
        assertThatThrownBy(() -> config.dataGsmOAuthClient("client-id", "client-secret", Duration.ZERO, RESPONSE_TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.dataGsmOpenApiClient("api-key", CONNECT_TIMEOUT, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
