package com.checkup.checkup.domain.qr.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class QrTokenGeneratorTest {

    private final QrTokenGenerator generator = new QrTokenGenerator();

    @Test
    void 토큰은_URL에_안전한_43자_문자열이다() {
        assertThat(generator.generate()).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void 매번_다른_토큰을_만든다() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(generator.generate());
        }

        assertThat(tokens).hasSize(1000);
    }

    @Test
    void 생성한_토큰은_항상_형식_검사를_통과한다() {
        for (int i = 0; i < 1000; i++) {
            assertThat(QrTokenGenerator.isWellFormed(generator.generate())).isTrue();
        }
    }

    @Test
    void 길이나_문자가_다른_값은_형식_검사를_통과하지_못한다() {
        String token = generator.generate();

        assertThat(QrTokenGenerator.isWellFormed(token.substring(1))).isFalse();
        assertThat(QrTokenGenerator.isWellFormed(token + "a")).isFalse();
        assertThat(QrTokenGenerator.isWellFormed(token.substring(1) + "+")).isFalse();
        assertThat(QrTokenGenerator.isWellFormed("")).isFalse();
        assertThat(QrTokenGenerator.isWellFormed(null)).isFalse();
    }
}
