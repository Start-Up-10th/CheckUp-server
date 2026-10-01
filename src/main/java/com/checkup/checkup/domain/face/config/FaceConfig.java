package com.checkup.checkup.domain.face.config;

import com.checkup.checkup.domain.face.controller.FacePayloadLimitFilter;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(FaceProperties.class)
public class FaceConfig {

    @Bean
    RestClient faceAiRestClient(RestClient.Builder builder, FaceProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
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
