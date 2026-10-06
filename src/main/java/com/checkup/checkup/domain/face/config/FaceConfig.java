package com.checkup.checkup.domain.face.config;

import com.checkup.checkup.domain.face.controller.FacePayloadLimitFilter;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(FaceProperties.class)
public class FaceConfig {

    @Bean
    RestClient faceAiRestClient(RestClient.Builder builder, FaceProperties properties) {
        // 내부 FastAPI(Uvicorn) 서버는 평문 HTTP/2 업그레이드를 협상하지 않는다.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.responseTimeout());
        return builder
                .baseUrl(properties.aiBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    FacePayloadLimitFilter facePayloadLimitFilter(FaceProperties properties, ObjectMapper objectMapper) {
        return new FacePayloadLimitFilter(properties, objectMapper);
    }
}
