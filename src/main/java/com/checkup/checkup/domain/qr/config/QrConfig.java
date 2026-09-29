package com.checkup.checkup.domain.qr.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * QR 설정값을 등록한다.
 */
@Configuration
@EnableConfigurationProperties(QrProperties.class)
public class QrConfig {
}
