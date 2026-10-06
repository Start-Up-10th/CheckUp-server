package com.checkup.checkup.domain.face.ai;

import com.checkup.checkup.domain.face.config.FaceProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in, no-biometric-data smoke test for the configured deployed AI service. */
@EnabledIfEnvironmentVariable(named = "FACE_AI_LIVE_TESTS", matches = "true")
class AiFaceClientLiveIntegrationTest {

    @Test
    void readinessAndAuthenticatedIdempotentDeleteWorkAgainstLiveAi() {
        String baseUrl = required("FACE_AI_BASE_URL");
        String serviceToken = required("FACE_SERVICE_TOKEN");
        URI baseUri = URI.create(baseUrl);
        boolean localHttp = "http".equalsIgnoreCase(baseUri.getScheme())
                && ("localhost".equalsIgnoreCase(baseUri.getHost())
                || "127.0.0.1".equals(baseUri.getHost())
                || "::1".equals(baseUri.getHost()));
        boolean explicitlyTrustedHttp = "true".equalsIgnoreCase(
                System.getenv("FACE_AI_LIVE_ALLOW_HTTP"));
        assumeTrue("https".equalsIgnoreCase(baseUri.getScheme()) || localHttp || explicitlyTrustedHttp,
                "Live test will not send FACE_SERVICE_TOKEN over untrusted plain HTTP");

        Duration connectTimeout = Duration.ofSeconds(2);
        Duration responseTimeout = Duration.ofSeconds(30);
        FaceProperties properties = new FaceProperties(
                baseUrl, serviceToken, connectTimeout, responseTimeout,
                60_000_000L, 2_097_152L, Duration.ofMillis(200), 4,
                Duration.ofMinutes(5), 60_000L, "v1");
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(responseTimeout);
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.aiBaseUrl())
                .requestFactory(requestFactory)
                .build();
        AiFaceClient client = new AiFaceClient(restClient, properties, JsonMapper.builder().build());

        client.ensureReady();
        // The AI delete contract is idempotent, so this random, nonexistent ID cannot remove a live session.
        client.deleteSession(UUID.randomUUID());
    }

    private static String required(String name) {
        String value = System.getenv(name);
        assertThat(value).as("Environment variable %s", name).isNotBlank();
        return value;
    }
}
