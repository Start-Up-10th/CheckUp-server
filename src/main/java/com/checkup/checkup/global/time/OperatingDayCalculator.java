package com.checkup.checkup.global.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OperatingDayCalculator {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    public static final LocalTime BOUNDARY = LocalTime.of(8, 0);

    private final Clock clock;

    public LocalDate today() {
        return of(clock.instant());
    }

    public LocalDate of(Instant instant) {
        return instant.atZone(ZONE)
                .minusHours(BOUNDARY.getHour())
                .toLocalDate();
    }

    public Instant startOf(LocalDate operatingDay) {
        return operatingDay.atTime(BOUNDARY).atZone(ZONE).toInstant();
    }

    public Instant nextBoundary() {
        return startOf(today().plusDays(1));
    }
}
