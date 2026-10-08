package com.checkup.checkup.domain.face.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * 얼굴 AI 서버 호출 시간이 호출 종류별로 지표에 남고, 지표의 경로에 세션 id가 들어가지 않는지 검증한다.
 */
@SpringBootTest
class AiFaceClientMetricsTest {

    private static final String FRAME_URI = "/internal/v1/face/sessions/{sessionId}/frames";
    private static final HttpServer FAKE_AI = startFakeAi();

    @Autowired
    private AiFaceClient aiFaceClient;

    @Autowired
    private MeterRegistry meterRegistry;

    @DynamicPropertySource
    static void aiBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("checkup.face.ai-base-url", () -> "http://127.0.0.1:" + FAKE_AI.getAddress().getPort());
    }

    @AfterAll
    static void stopFakeAi() {
        FAKE_AI.stop(0);
    }

    @Test
    @DisplayName("프레임 인식 호출 시간이 세션 id 없는 경로로 지표에 남는다")
    void frameCallIsTimedByUriTemplate() {
        UUID sessionId = UUID.randomUUID();

        aiFaceClient.recognize(sessionId, "frame-1", new byte[] {1, 2, 3}, MediaType.IMAGE_JPEG);

        Timer timer = meterRegistry.get("http.client.requests")
                .tag("uri", FRAME_URI)
                .tag("method", "POST")
                .tag("status", "200")
                .timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(meterRegistry.find("http.client.requests").timers())
                .noneMatch(found -> found.getId().getTag("uri").contains(sessionId.toString()));
    }

    @Test
    @DisplayName("준비 상태 확인 호출은 프레임 인식과 다른 경로로 따로 남는다")
    void readinessCallIsTimedSeparately() {
        aiFaceClient.ensureReady();

        assertThat(meterRegistry.get("http.client.requests").tag("uri", "/health/ready").timer().count())
                .isGreaterThanOrEqualTo(1);
    }

    private static HttpServer startFakeAi() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/health/ready", exchange -> respond(exchange, "{\"status\":\"ready\"}"));
            server.createContext("/internal/v1/face/sessions/", exchange -> respond(exchange,
                    "{\"frameId\":\"frame-1\",\"frame_id\":\"frame-1\",\"faces\":[]}"));
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(HttpExchange exchange, String json) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
