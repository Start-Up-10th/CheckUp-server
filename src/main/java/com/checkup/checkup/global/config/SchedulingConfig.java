package com.checkup.checkup.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled} 작업을 켠다. 지난 운영일 출석 알림 폐기 등 정해진 시각에 도는 작업이 쓴다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
