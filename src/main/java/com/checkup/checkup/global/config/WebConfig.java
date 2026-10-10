package com.checkup.checkup.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 웹 프런트 설정값, 관리자 허용 목록, 학생 제외 목록 설정을 등록한다.
 */
@Configuration
@EnableConfigurationProperties({WebProperties.class, AdminProperties.class, StudentExclusionProperties.class})
public class WebConfig {
}
