package com.checkup.checkup.domain.volunteer.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 한글 이름 검색이 포함·초성·오타 1개를 순위대로 판정하고, 너무 다른 이름은 걸러내는지 검증한다.
 */
class KoreanNameMatcherTest {

    @Test
    @DisplayName("한글을 초성과 자음·모음으로 쪼갠다")
    void decompose() {
        assertThat(KoreanNameMatcher.initials("강민우")).isEqualTo("ㄱㅁㅇ");
        assertThat(KoreanNameMatcher.jamo("강민우")).isEqualTo("ㄱㅏㅇㅁㅣㄴㅇㅜ");
        assertThat(KoreanNameMatcher.distance("ㄱㅏㅇ", "ㄱㅏㅁ")).isEqualTo(1);
    }
}
