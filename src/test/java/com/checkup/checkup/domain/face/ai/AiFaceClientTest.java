package com.checkup.checkup.domain.face.ai;

import com.checkup.checkup.domain.face.config.FaceProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

class AiFaceClientTest {

    @Test
    @DisplayName("준비 상태 확인은 서비스 토큰 없이 공개 health 경로를 쓴다")
    void readinessUsesPublicHealthEndpointWithoutServiceBearer() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://face-ai.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FaceProperties properties = new FaceProperties("http://face-ai.test", "face-secret",
                Duration.ofSeconds(2), Duration.ofSeconds(30), 1024, 512,
                Duration.ofMillis(200), 2, Duration.ofMinutes(5), 60_000, "v1");
        AiFaceClient client = new AiFaceClient(builder.build(), properties, JsonMapper.builder().build());

        server.expect(requestTo("http://face-ai.test/health/ready"))
                .andExpect(method(GET))
                .andExpect(request -> assertThat(request.getHeaders().getFirst("Authorization")).isNull())
                .andRespond(withSuccess("{\"status\":\"ready\"}", MediaType.APPLICATION_JSON));

        client.ensureReady();

        server.verify();
    }

    @Test
    @DisplayName("AI가 준비되지 않았으면 503으로 세션 준비를 거부한다")
    void notReadyResponseRejectsSessionSetup() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://face-ai.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FaceProperties properties = new FaceProperties("http://face-ai.test", "face-secret",
                Duration.ofSeconds(2), Duration.ofSeconds(30), 1024, 512,
                Duration.ofMillis(200), 2, Duration.ofMinutes(5), 60_000, "v1");
        AiFaceClient client = new AiFaceClient(builder.build(), properties, JsonMapper.builder().build());

        server.expect(requestTo("http://face-ai.test/health/ready"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"status\":\"not_ready\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(client::ensureReady)
                .isInstanceOfSatisfying(AiFaceException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(503));

        server.verify();
    }

    @Test
    @DisplayName("영상 원본과 서비스 Bearer만 AI로 보낸다")
    void sendsOnlyVideoAndServiceBearerToAi() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://face-ai.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FaceProperties properties = new FaceProperties("http://face-ai.test", "face-secret",
                Duration.ofSeconds(2), Duration.ofSeconds(30), 1024, 512,
                Duration.ofMillis(200), 2, Duration.ofMinutes(5), 60_000, "v1");
        AiFaceClient client = new AiFaceClient(builder.build(), properties, JsonMapper.builder().build());
        byte[] video = new byte[]{7, 3, 2, 1};

        server.expect(requestTo("http://face-ai.test/internal/v1/face/enrollments/extract"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer face-secret"))
                .andExpect(header("Content-Type", "video/webm"))
                .andExpect(content().bytes(video))
                .andRespond(withSuccess("""
                        {"model":{"model_id":"model-a","version":"v1","dimension":256,"normalization":"l2"},
                         "reviewedFrames":1,"acceptedFrames":1,"vectors":[[]]}
                        """, MediaType.APPLICATION_JSON));

        AiFaceEnrollmentResponse response = client.extract(video, MediaType.parseMediaType("video/webm"));

        assertThat(response.model().modelId()).isEqualTo("model-a");
        server.verify();
    }

    @Test
    @DisplayName("세션 요청은 FastAPI가 기대하는 snake_case 필드로 직렬화한다")
    void sessionRequestUsesSnakeCaseFields() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://face-ai.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FaceProperties properties = new FaceProperties("http://face-ai.test", "face-secret",
                Duration.ofSeconds(2), Duration.ofSeconds(30), 1024, 512,
                Duration.ofMillis(200), 2, Duration.ofMinutes(5), 60_000, "v1");
        AiFaceClient client = new AiFaceClient(builder.build(), properties, JsonMapper.builder().build());
        UUID sessionId = UUID.randomUUID();
        AiFaceSessionRequest request = new AiFaceSessionRequest(
                new AiFaceModel("model-a", "v1", 256, "l2"),
                List.of(new AiFaceCandidate("student-a", List.of(List.of(0.1)))));

        server.expect(requestTo("http://face-ai.test/health/ready"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"status\":\"ready\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://face-ai.test/internal/v1/face/sessions/" + sessionId))
                .andExpect(method(PUT))
                .andExpect(header("Authorization", "Bearer face-secret"))
                .andExpect(content().json("""
                        {"model":{"model_id":"model-a","version":"v1","dimension":256,"normalization":"l2"},
                         "candidates":[{"student_id":"student-a","vectors":[[0.1]]}]}
                        """))
                .andRespond(withSuccess("{\"status\":\"ready\"}", MediaType.APPLICATION_JSON));

        client.createSession(sessionId, request);

        server.verify();
    }
}
