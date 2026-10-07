package com.checkup.checkup.global.querylog;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * 요청 하나가 DB에 보낸 SQL 문장 수를 센다.
 *
 * Hibernate가 SQL을 실행하기 직전에 부르는 StatementInspector로 등록되어, JPA 메서드·JPQL·native 쿼리를 모두 센다.
 * 수는 스레드마다 따로 세므로 동시에 들어온 다른 요청과 섞이지 않는다.
 * Redis 호출과 요청 스레드 밖에서 실행된 쿼리는 세지 않는다.
 */
public class QueryCounter implements StatementInspector {

    private final ThreadLocal<Integer> count = new ThreadLocal<>();

    /** 현재 스레드의 수를 0으로 두고 세기 시작한다. */
    public void start() {
        count.set(0);
    }

    /**
     * 세기를 끝내고 현재 스레드의 값을 지운다.
     *
     * @return 시작한 뒤 실행된 SQL 문장 수. 시작하지 않았으면 0이다
     */
    public int stop() {
        Integer current = count.get();
        count.remove();
        return current == null ? 0 : current;
    }

    /**
     * SQL 문장 하나를 센다. 세기를 시작하지 않은 스레드(스케줄 작업 등)에서는 아무것도 하지 않는다.
     *
     * @return 받은 SQL 그대로. 문장을 바꾸지 않는다
     */
    @Override
    public String inspect(String sql) {
        Integer current = count.get();
        if (current != null) {
            count.set(current + 1);
        }
        return sql;
    }
}
