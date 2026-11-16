package com.checkup.checkup.global.querylog;

import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JdbcTemplate이 실행한 SQL도 QueryCounter로 센다.
 *
 * JdbcTemplate은 Hibernate를 거치지 않아 StatementInspector에 잡히지 않는다.
 * 문장을 실행하기 직전에 한 번 불리는 설정 단계에서 세므로, 배치 실행은 한 번으로 센다.
 */
class CountingJdbcTemplate extends JdbcTemplate {

    private final QueryCounter queryCounter;

    CountingJdbcTemplate(DataSource dataSource, QueryCounter queryCounter) {
        super(dataSource);
        this.queryCounter = queryCounter;
    }

    @Override
    protected void applyStatementSettings(Statement statement) throws SQLException {
        queryCounter.count();
        super.applyStatementSettings(statement);
    }
}
