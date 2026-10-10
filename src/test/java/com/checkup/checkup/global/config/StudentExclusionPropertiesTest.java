package com.checkup.checkup.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 학생 제외 목록 설정이 비어 있어도 안전하고, 목록에 있는 DataGSM 학생 id만 제외하는지 검증한다(DEC-034).
 */
class StudentExclusionPropertiesTest {

    @Test
    @DisplayName("설정이 없으면 빈 목록이고 아무도 제외하지 않는다")
    void missingSettingExcludesNobody() {
        StudentExclusionProperties properties = new StudentExclusionProperties(null);

        assertThat(properties.excludedDatagsmIds()).isEmpty();
        assertThat(properties.isExcluded(1L)).isFalse();
    }

    @Test
    @DisplayName("목록에 있는 학생 id만 제외하고 null은 제외하지 않는다")
    void excludesOnlyListedIds() {
        StudentExclusionProperties properties = new StudentExclusionProperties(Set.of(7L, 1_000L));

        assertThat(properties.isExcluded(7L)).isTrue();
        assertThat(properties.isExcluded(Long.valueOf(1_000L))).isTrue();
        assertThat(properties.isExcluded(8L)).isFalse();
        assertThat(properties.isExcluded(null)).isFalse();
    }
}
