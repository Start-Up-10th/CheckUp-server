package com.checkup.checkup.global.querylog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * QueryCounter가 시작한 스레드의 SQL 문장만 세고 문장을 바꾸지 않는지 검증한다.
 */
class QueryCounterTest {

    private final QueryCounter queryCounter = new QueryCounter();

    @Test
    @DisplayName("시작한 뒤 실행된 문장 수를 세고 끝내면 다시 0부터 센다")
    void countsStatementsBetweenStartAndStop() {
        queryCounter.start();
        queryCounter.inspect("select 1");
        queryCounter.inspect("select 2");

        assertThat(queryCounter.stop()).isEqualTo(2);

        queryCounter.start();
        assertThat(queryCounter.stop()).isZero();
    }

    @Test
    @DisplayName("시작하지 않은 스레드의 문장은 세지 않는다")
    void ignoresStatementsWithoutStart() {
        queryCounter.inspect("select 1");

        assertThat(queryCounter.stop()).isZero();
    }

    @Test
    @DisplayName("다른 스레드에서 실행된 문장은 섞이지 않는다")
    void doesNotMixOtherThreads() {
        queryCounter.start();
        queryCounter.inspect("select 1");

        CompletableFuture.runAsync(() -> {
            queryCounter.start();
            queryCounter.inspect("select 2");
            queryCounter.inspect("select 3");
        }).join();

        assertThat(queryCounter.stop()).isEqualTo(1);
    }

    @Test
    @DisplayName("SQL 문장을 그대로 돌려준다")
    void returnsSqlUnchanged() {
        assertThat(queryCounter.inspect("select 1")).isEqualTo("select 1");
    }
}
