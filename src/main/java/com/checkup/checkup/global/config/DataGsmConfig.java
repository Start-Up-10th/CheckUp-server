package com.checkup.checkup.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import team.themoment.datagsm.sdk.oauth.DataGsmOAuthClient;

/**
 * DataGSM OAuth SDK 클라이언트 설정. Client ID·Secret은 환경변수에서 읽는다.
 */
@Configuration
public class DataGsmConfig {

    @Bean
    public DataGsmOAuthClient dataGsmOAuthClient(
            @Value("${datagsm.client-id}") String clientId,
            @Value("${datagsm.client-secret}") String clientSecret
    ) {
        return DataGsmOAuthClient.builder(clientId, clientSecret).build();
    }
}
