package com.checkup.checkup.global.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

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
    void 오전_8시_정각은_당일_운영일이다() {
        assertThat(at(LocalDateTime.of(2026, 9, 26, 8, 0)).today())
                .isEqualTo(LocalDate.of(2026, 9, 26));
    }

    @Test
    void 오전_8시_직전은_전날_운영일이다() {
        assertThat(at(LocalDateTime.of(2026, 9, 26, 7, 59, 59, 999_999_999)).today())
                .isEqualTo(LocalDate.of(2026, 9, 25));
    }

    @Test
    void 자정이_지나도_오전_8시_전이면_전날_운영일이다() {
        assertThat(at(LocalDateTime.of(2026, 9, 26, 0, 30)).today())
                .isEqualTo(LocalDate.of(2026, 9, 25));
    }

    @Test
    void 서버_시계가_UTC여도_KST_기준으로_계산한다() {
        Instant utc = LocalDateTime.of(2026, 9, 25, 23, 0).toInstant(ZoneOffset.UTC);

        assertThat(at(LocalDateTime.of(2026, 1, 1, 12, 0)).of(utc))
                .isEqualTo(LocalDate.of(2026, 9, 26));
    }

    @Test
    void 운영일_시작_시각은_해당_날짜_오전_8시다() {
        assertThat(at(LocalDateTime.of(2026, 9, 26, 12, 0)).startOf(LocalDate.of(2026, 9, 26)))
                .isEqualTo(kst(2026, 9, 26, 8, 0));
    }

    @Test
    void 오전_8시_전의_다음_경계는_당일_오전_8시다() {
        assertThat(at(LocalDateTime.of(2026, 9, 26, 7, 50)).nextBoundary())
                .isEqualTo(kst(2026, 9, 26, 8, 0));
    }

    @Test
    void 오전_8시_정각의_다음_경계는_다음날_오전_8시다() {
        assertThat(at(LocalDateTime.of(2026, 9, 26, 8, 0)).nextBoundary())
                .isEqualTo(kst(2026, 9, 27, 8, 0));
    }

    @Test
    void 월말_연말도_날짜를_넘긴다() {
        assertThat(at(LocalDateTime.of(2027, 1, 1, 3, 0)).today())
                .isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(at(LocalDateTime.of(2026, 12, 31, 23, 0)).nextBoundary())
                .isEqualTo(kst(2027, 1, 1, 8, 0));
    }
}
