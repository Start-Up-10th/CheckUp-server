package com.checkup.checkup.global.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OperatingDayCalculatorTest {

    private static OperatingDayCalculator at(LocalDateTime kst) {
        Instant instant = kst.atZone(OperatingDayCalculator.ZONE).toInstant();
        return new OperatingDayCalculator(Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static Instant kst(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute)
                .atZone(OperatingDayCalculator.ZONE)
                .toInstant();
    }

    @Test
    @DisplayName("오전 8시 정각은 당일 운영일이다")
    void eightOClockIsSameOperatingDay() {
        assertThat(at(LocalDateTime.of(2026, 9, 26, 8, 0)).today())
                .isEqualTo(LocalDate.of(2026, 9, 26));
    }

    @Test
    @DisplayName("오전 8시 직전은 전날 운영일이다")
    void justBefore8IsPreviousOperatingDay() {
        assertThat(at(LocalDateTime.of(2026, 9, 26, 7, 59, 59, 999_999_999)).today())
                .isEqualTo(LocalDate.of(2026, 9, 25));
    }

    @Test
    @DisplayName("자정이 지나도 오전 8시 전이면 전날 운영일이다")
    void afterMidnightBefore8IsPreviousOperatingDay() {
        assertThat(at(LocalDateTime.of(2026, 9, 26, 0, 30)).today())
                .isEqualTo(LocalDate.of(2026, 9, 25));
    }

    @Test
    @DisplayName("서버 시계가 UTC여도 KST 기준으로 계산한다")
    void usesKstEvenWithUtcClock() {
        Instant utc = LocalDateTime.of(2026, 9, 25, 23, 0).toInstant(ZoneOffset.UTC);

        assertThat(at(LocalDateTime.of(2026, 1, 1, 12, 0)).of(utc))
                .isEqualTo(LocalDate.of(2026, 9, 26));
    }

    @Test
    @DisplayName("운영일 시작 시각은 해당 날짜 오전 8시다")
    void operatingDayStartsAt8() {
        assertThat(at(LocalDateTime.of(2026, 9, 26, 12, 0)).startOf(LocalDate.of(2026, 9, 26)))
                .isEqualTo(kst(2026, 9, 26, 8, 0));
    }

    @Test
    @DisplayName("오전 8시 전의 다음 경계는 당일 오전 8시다")
    void nextBoundaryBefore8IsSameDay8() {
        assertThat(at(LocalDateTime.of(2026, 9, 26, 7, 50)).nextBoundary())
                .isEqualTo(kst(2026, 9, 26, 8, 0));
    }

    @Test
    @DisplayName("오전 8시 정각의 다음 경계는 다음날 오전 8시다")
    void nextBoundaryAt8IsNextDay8() {
        assertThat(at(LocalDateTime.of(2026, 9, 26, 8, 0)).nextBoundary())
                .isEqualTo(kst(2026, 9, 27, 8, 0));
    }

    @Test
    @DisplayName("월말 연말도 날짜를 넘긴다")
    void crossesMonthAndYearEnd() {
        assertThat(at(LocalDateTime.of(2027, 1, 1, 3, 0)).today())
                .isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(at(LocalDateTime.of(2026, 12, 31, 23, 0)).nextBoundary())
                .isEqualTo(kst(2027, 1, 1, 8, 0));
    }
}
