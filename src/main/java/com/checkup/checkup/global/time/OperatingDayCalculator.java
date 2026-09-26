package com.checkup.checkup.global.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 운영일은 Asia/Seoul 08:00부터 다음 날 07:59:59.999까지다(DEC-006).
 * 자정이 아니라 08:00에 바뀌므로 KST 시각에서 8시간을 뺀 날짜를 운영일 키로 쓴다.
 */
@Component
@RequiredArgsConstructor
public class OperatingDayCalculator {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    public static final LocalTime BOUNDARY = LocalTime.of(8, 0);

    private final Clock clock;

    /** 현재 운영일. */
    public LocalDate today() {
        return of(clock.instant());
    }

    /** 주어진 시각이 속한 운영일. */
    public LocalDate of(Instant instant) {
        return instant.atZone(ZONE)
                .minusHours(BOUNDARY.getHour())
                .toLocalDate();
    }

    /** 운영일이 시작되는 시각(해당 날짜 08:00 KST). */
    public Instant startOf(LocalDate operatingDay) {
        return operatingDay.atTime(BOUNDARY).atZone(ZONE).toInstant();
    }

    /** 현재 이후 처음 오는 운영일 경계(다음 08:00 KST). */
    public Instant nextBoundary() {
        return startOf(today().plusDays(1));
    }
}
