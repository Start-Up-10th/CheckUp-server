package com.checkup.checkup.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 관리자 허용 목록 설정이 비어 있어도 안전하고, 목록에 있는 DataGSM 계정 id만 허용하는지 검증한다.
 */
class AdminPropertiesTest {

    @Test
    @DisplayName("설정이 없으면 빈 목록이고 아무도 허용하지 않는다")
    void missingSettingAllowsNobody() {
        AdminProperties properties = new AdminProperties(null);

        assertThat(properties.datagsmIds()).isEmpty();
        assertThat(properties.isAllowed(1L)).isFalse();
    }

    @Test
    @DisplayName("목록에 있는 계정 id만 허용하고 null은 허용하지 않는다")
    void allowsOnlyListedIds() {
        AdminProperties properties = new AdminProperties(Set.of(12L, 1_000L));

        assertThat(properties.isAllowed(12L)).isTrue();
        assertThat(properties.isAllowed(Long.valueOf(1_000L))).isTrue();
        assertThat(properties.isAllowed(13L)).isFalse();
        assertThat(properties.isAllowed(null)).isFalse();
    }
}
