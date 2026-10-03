package com.checkup.checkup.domain.qr.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * QR 토큰이 URL에 안전한 고정 길이 랜덤 값이고, 형식 검사가 다른 값을 거르는지 검증한다.
 */
class QrTokenGeneratorTest {

    private final QrTokenGenerator generator = new QrTokenGenerator();

    @Test
    @DisplayName("토큰은 URL에 안전한 43자 문자열이다")
    void tokenIsUrlSafe43Characters() {
        assertThat(generator.generate()).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    @DisplayName("매번 다른 토큰을 만든다")
    void generatesDifferentTokens() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(generator.generate());
        }

        assertThat(tokens).hasSize(1000);
    }

    @Test
    @DisplayName("생성한 토큰은 항상 형식 검사를 통과한다")
    void generatedTokenIsWellFormed() {
        for (int i = 0; i < 1000; i++) {
            assertThat(QrTokenGenerator.isWellFormed(generator.generate())).isTrue();
        }
    }

    @Test
    @DisplayName("길이나 문자가 다른 값은 형식 검사를 통과하지 못한다")
    void wrongLengthOrCharactersIsNotWellFormed() {
        String token = generator.generate();

        assertThat(QrTokenGenerator.isWellFormed(token.substring(1))).isFalse();
        assertThat(QrTokenGenerator.isWellFormed(token + "a")).isFalse();
        assertThat(QrTokenGenerator.isWellFormed(token.substring(1) + "+")).isFalse();
        assertThat(QrTokenGenerator.isWellFormed("")).isFalse();
        assertThat(QrTokenGenerator.isWellFormed(null)).isFalse();
    }
}
