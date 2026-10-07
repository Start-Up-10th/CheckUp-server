package com.checkup.checkup.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.zaxxer.hikari.HikariDataSource;

/**
 * DB 커넥션 풀 크기와 대기 시간이 설정(기본 10개·10초)대로 적용되는지 검증한다(#217).
 */
@SpringBootTest
class HikariPoolSettingsTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("커넥션 풀은 기본 10개이고 가득 차면 10초 뒤 실패한다")
    void poolUsesConfiguredDefaults() throws Exception {
        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);

        assertThat(hikari.getMaximumPoolSize()).isEqualTo(10);
        assertThat(hikari.getConnectionTimeout()).isEqualTo(10_000L);
    }
}
