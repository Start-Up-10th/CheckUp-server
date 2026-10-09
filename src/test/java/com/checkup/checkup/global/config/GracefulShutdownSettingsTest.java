package com.checkup.checkup.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 종료 신호를 받았을 때 처리 중인 요청을 기다리는 설정이 기본값으로 켜져 있고,
 * 환경변수로 대기 시간을 바꿀 수 있는지 DB 없이 검증한다(#233).
 */
class GracefulShutdownSettingsTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    @DisplayName("우아한 종료가 켜져 있고 처리 중인 요청을 30초까지 기다린다")
    void gracefulShutdownIsEnabledByDefault() {
        contextRunner.run(context -> {
            Binder binder = Binder.get(context.getEnvironment());

            assertThat(binder.bind("server.shutdown", String.class).get()).isEqualToIgnoringCase("graceful");
            assertThat(binder.bind("spring.lifecycle.timeout-per-shutdown-phase", Duration.class).get())
                    .isEqualTo(Duration.ofSeconds(30));
        });
    }

    @Test
    @DisplayName("SHUTDOWN_TIMEOUT으로 대기 시간을 바꾼다")
    void timeoutCanBeOverriddenByEnvironment() {
        contextRunner
                .withPropertyValues("SHUTDOWN_TIMEOUT=45s")
                .run(context -> assertThat(Binder.get(context.getEnvironment())
                        .bind("spring.lifecycle.timeout-per-shutdown-phase", Duration.class).get())
                        .isEqualTo(Duration.ofSeconds(45)));
    }
}
