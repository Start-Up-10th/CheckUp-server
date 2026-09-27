package com.checkup.checkup.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 테스트에서 시각을 직접 옮길 수 있는 Clock.
 */
public class MutableClock extends Clock {

    private Instant instant;

    public MutableClock(Instant instant) {
        this.instant = instant;
    }

    /**
     * 현재 시각을 바꾼다.
     */
    public void setInstant(Instant instant) {
        this.instant = instant;
    }

    /**
     * 현재 시각을 앞으로 옮긴다.
     */
    public void advance(Duration duration) {
        this.instant = instant.plus(duration);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
