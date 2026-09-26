package com.checkup.checkup.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import team.themoment.datagsm.sdk.oauth.DataGsmOAuthClient;

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
