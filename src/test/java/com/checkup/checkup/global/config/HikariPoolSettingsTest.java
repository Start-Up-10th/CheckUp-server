package com.checkup.checkup.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;

import com.zaxxer.hikari.HikariDataSource;

/**
 * DB 커넥션 풀 크기와 대기 시간이 application.yaml 기본값(10개·10초)으로 적용되고,
 * 환경변수로 덮어쓸 수 있는지 DB 없이 검증한다(#217).
 */
class HikariPoolSettingsTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class));

    @Test
    @DisplayName("커넥션 풀은 기본 10개이고 가득 차면 10초 뒤 실패한다")
    void poolUsesConfiguredDefaults() {
        contextRunner.run(context -> {
            HikariDataSource hikari = context.getBean(HikariDataSource.class);

            assertThat(hikari.getMaximumPoolSize()).isEqualTo(10);
            assertThat(hikari.getConnectionTimeout()).isEqualTo(10_000L);
        });
    }

    @Test
    @DisplayName("DB_POOL_SIZE와 DB_POOL_TIMEOUT_MS로 풀 크기와 대기 시간을 바꾼다")
    void poolSettingsCanBeOverriddenByEnvironment() {
        contextRunner
                .withPropertyValues("DB_POOL_SIZE=20", "DB_POOL_TIMEOUT_MS=3000")
                .run(context -> {
                    HikariDataSource hikari = context.getBean(HikariDataSource.class);

                    assertThat(hikari.getMaximumPoolSize()).isEqualTo(20);
                    assertThat(hikari.getConnectionTimeout()).isEqualTo(3_000L);
                });
    }
}
