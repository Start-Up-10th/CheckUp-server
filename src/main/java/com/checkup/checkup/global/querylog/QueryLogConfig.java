package com.checkup.checkup.global.querylog;

import org.hibernate.cfg.JdbcSettings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 요청별 DB 쿼리 수 로그를 켠다. {@code checkup.query-log.enabled}(환경변수 {@code QUERY_LOG_ENABLED})가 true일 때만 동작한다.
 *
 * 기본은 꺼져 있고, 꺼져 있으면 아래 빈이 만들어지지 않아 요청 처리에 아무것도 더하지 않는다.
 * 느린 API를 찾거나 쿼리 수를 줄인 결과를 확인할 때 켠다.
 */
@Configuration
@ConditionalOnProperty(name = "checkup.query-log.enabled", havingValue = "true")
public class QueryLogConfig {

    @Bean
    public QueryCounter queryCounter() {
        return new QueryCounter();
    }

    /** Hibernate가 SQL을 실행할 때마다 QueryCounter를 부르게 한다. */
    @Bean
    public HibernatePropertiesCustomizer queryCounterCustomizer(QueryCounter queryCounter) {
        return properties -> properties.put(JdbcSettings.STATEMENT_INSPECTOR, queryCounter);
    }

    /** 인증 필터에서 실행된 쿼리와 걸린 시간도 포함하도록 가장 바깥에 둔다. */
    @Bean
    public FilterRegistrationBean<QueryCountFilter> queryCountFilter(QueryCounter queryCounter) {
        FilterRegistrationBean<QueryCountFilter> registration =
                new FilterRegistrationBean<>(new QueryCountFilter(queryCounter));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
