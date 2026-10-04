package com.checkup.checkup.global.config;

import java.time.Duration;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import team.themoment.datagsm.sdk.oauth.DataGsmOAuthClient;
import team.themoment.datagsm.sdk.openapi.DataGsmOpenApiClient;

/**
 * DataGSM SDK 클라이언트 설정. Client ID·Secret·API Key는 환경변수에서 읽는다.
 *
 * SDK 기본 대기 시간은 연결·응답 각각 30초라 DataGSM에 닿지 않으면 요청이 오래 매달린다.
 * 연결·응답 대기 시간을 따로 두고, 요청 하나 전체도 (연결 + 응답) 시간 안에 끝나게 한다.
 */
@Configuration
public class DataGsmConfig {

    @Bean
    public DataGsmOAuthClient dataGsmOAuthClient(
            @Value("${datagsm.client-id}") String clientId,
            @Value("${datagsm.client-secret}") String clientSecret,
            @Value("${datagsm.connect-timeout}") Duration connectTimeout,
            @Value("${datagsm.response-timeout}") Duration responseTimeout
    ) {
        return DataGsmOAuthClient.builder(clientId, clientSecret)
                .httpClient(oauthHttpClient(connectTimeout, responseTimeout))
                .build();
    }

    @Bean(destroyMethod = "close")
    public DataGsmOpenApiClient dataGsmOpenApiClient(
            @Value("${datagsm.api-key}") String apiKey,
            @Value("${datagsm.connect-timeout}") Duration connectTimeout,
            @Value("${datagsm.response-timeout}") Duration responseTimeout
    ) {
        return DataGsmOpenApiClient.builder(apiKey)
                .httpClient(new team.themoment.datagsm.sdk.openapi.http.OkHttpClientImpl(
                        okHttpClient(connectTimeout, responseTimeout)))
                .build();
    }

    /** 대기 시간이 걸린 OAuth SDK용 HTTP 클라이언트를 만든다. */
    static team.themoment.datagsm.sdk.oauth.http.HttpClient oauthHttpClient(
            Duration connectTimeout,
            Duration responseTimeout
    ) {
        return new team.themoment.datagsm.sdk.oauth.http.OkHttpClientImpl(
                okHttpClient(connectTimeout, responseTimeout));
    }

    private static OkHttpClient okHttpClient(Duration connectTimeout, Duration responseTimeout) {
        if (connectTimeout.isZero() || connectTimeout.isNegative()
                || responseTimeout.isZero() || responseTimeout.isNegative()) {
            throw new IllegalArgumentException("datagsm connect-timeout and response-timeout must be positive");
        }
        return new OkHttpClient.Builder()
                .connectTimeout(connectTimeout)
                .readTimeout(responseTimeout)
                .writeTimeout(responseTimeout)
                .callTimeout(connectTimeout.plus(responseTimeout))
                .build();
    }
}
