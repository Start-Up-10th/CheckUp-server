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
}
