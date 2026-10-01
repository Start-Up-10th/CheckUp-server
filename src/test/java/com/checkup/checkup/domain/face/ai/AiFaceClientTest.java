package com.checkup.checkup.domain.face.ai;

import com.checkup.checkup.domain.face.config.FaceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class AiFaceClientTest {

    @Test
    void 영상_원본과_서비스_Bearer만_AI로_보낸다() {
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
}
