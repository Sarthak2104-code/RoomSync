package com.roomsync.common.time;

import com.roomsync.booking.exception.InvalidBookingTimeException;
import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.InvalidTimeZoneException;
import com.roomsync.location.entity.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimezoneServiceTest {

    private DateTimeProvider dateTimeProvider;
    private TimezoneService timezoneService;

    // Fixed instant: 2026-08-24 10:00:00 UTC
    private final Instant fixedInstant = Instant.parse("2026-08-24T10:00:00Z");

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        dateTimeProvider = new SystemDateTimeProvider(fixedClock);
        timezoneService = new TimezoneService(dateTimeProvider);
    }

    @Test
    @DisplayName("Valid IANA timezones resolve correctly")
    void testValidIanaTimezones() {
        assertThat(timezoneService.isValidZoneId("Asia/Kolkata")).isTrue();
        assertThat(timezoneService.isValidZoneId("America/New_York")).isTrue();
        assertThat(timezoneService.isValidZoneId("Europe/London")).isTrue();
        assertThat(timezoneService.isValidZoneId("UTC")).isTrue();

        ZoneId kolkata = timezoneService.resolveZoneId("Asia/Kolkata");
        assertThat(kolkata).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }

    @Test
    @DisplayName("Invalid IANA timezone throws InvalidTimeZoneException")
    void testInvalidIanaTimezone() {
        assertThat(timezoneService.isValidZoneId("Invalid/Timezone")).isFalse();
        assertThat(timezoneService.isValidZoneId("GMT+5:30")).isFalse();

        assertThatThrownBy(() -> timezoneService.resolveZoneId("Invalid/Timezone"))
                .isInstanceOf(InvalidTimeZoneException.class)
                .hasMessageContaining("Invalid IANA timezone identifier: 'Invalid/Timezone'");
    }

    @Test
    @DisplayName("Resolves authoritative timezone from Location entity")
    void testLocationTimezoneResolution() {
        Location location = Location.builder()
                .id(1L)
                .name("Mumbai HQ")
                .code("MUM")
                .timezone("Asia/Kolkata")
                .build();

        ZoneId zoneId = timezoneService.getLocationZoneId(location);
        assertThat(zoneId).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }

    @Test
    @DisplayName("Test 7: Nonexistent DST time during spring-forward gap is rejected deterministically")
    void testNonexistentDstTimeRejected() {
        // America/New_York springs forward on 2026-03-08 from 02:00 to 03:00 (EDT)
        // 02:30:00 local time does not exist
        ZoneId nyZone = ZoneId.of("America/New_York");
        LocalDate dstDate = LocalDate.of(2026, 3, 8);
        LocalTime nonexistentTime = LocalTime.of(2, 30);

        assertThat(timezoneService.isNonexistentTime(dstDate, nonexistentTime, nyZone)).isTrue();

        assertThatThrownBy(() -> timezoneService.toZonedDateTime(dstDate, nonexistentTime, nyZone))
                .isInstanceOf(InvalidBookingTimeException.class)
                .satisfies(ex -> {
                    InvalidBookingTimeException ibte = (InvalidBookingTimeException) ex;
                    assertThat(ibte.getErrorCode()).isEqualTo(ErrorCode.INVALID_TIME);
                })
                .hasMessageContaining("does not exist in timezone 'America/New_York' due to daylight saving time transition");
    }

    @Test
    @DisplayName("Test 8: Ambiguous DST time during fall-back overlap resolves deterministically to earlier offset")
    void testAmbiguousDstTimeResolvedDeterministically() {
        // America/New_York falls back on 2026-11-01 from 02:00 to 01:00 (EST)
        // 01:30:00 local time occurs twice: first at -04:00 (EDT), then at -05:00 (EST)
        ZoneId nyZone = ZoneId.of("America/New_York");
        LocalDate dstDate = LocalDate.of(2026, 11, 1);
        LocalTime ambiguousTime = LocalTime.of(1, 30);

        assertThat(timezoneService.isAmbiguousTime(dstDate, ambiguousTime, nyZone)).isTrue();

        ZonedDateTime resolved = timezoneService.toZonedDateTime(dstDate, ambiguousTime, nyZone);
        assertThat(resolved).isNotNull();
        // Deterministic rule: earlier offset (-04:00 EDT)
        assertThat(resolved.getOffset()).isEqualTo(ZoneOffset.ofHours(-4));
        assertThat(resolved.toInstant()).isEqualTo(Instant.parse("2026-11-01T05:30:00Z"));
    }

    @Test
    @DisplayName("Test 9: Resolves relative dates 'today' and 'tomorrow' within location timezone")
    void testRelativeDateResolution() {
        // At fixedInstant = 2026-08-24 10:00:00 UTC:
        // In Asia/Kolkata (+05:30), local time is 2026-08-24 15:30:00
        ZoneId kolkata = ZoneId.of("Asia/Kolkata");

        LocalDate todayKolkata = timezoneService.resolveRelativeDate("today", kolkata);
        LocalDate tomorrowKolkata = timezoneService.resolveRelativeDate("tomorrow", kolkata);

        assertThat(todayKolkata).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(tomorrowKolkata).isEqualTo(LocalDate.of(2026, 8, 25));

        // Case insensitivity
        assertThat(timezoneService.resolveRelativeDate("TODAY", kolkata)).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(timezoneService.resolveRelativeDate("Tomorrow", kolkata)).isEqualTo(LocalDate.of(2026, 8, 25));

        // ISO format fallback
        assertThat(timezoneService.resolveRelativeDate("2026-09-01", kolkata)).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    @DisplayName("Relative date calculation across timezone date boundary")
    void testRelativeDateAcrossTimezoneBoundary() {
        // At 2026-08-24 20:00:00 UTC:
        // In UTC: today is 2026-08-24
        // In Asia/Kolkata (+05:30): local time is 2026-08-25 01:30:00 -> today is 2026-08-25
        Instant eveningUtc = Instant.parse("2026-08-24T20:00:00Z");
        DateTimeProvider provider = new SystemDateTimeProvider(Clock.fixed(eveningUtc, ZoneOffset.UTC));
        TimezoneService tzService = new TimezoneService(provider);

        ZoneId kolkata = ZoneId.of("Asia/Kolkata");
        ZoneId utc = ZoneId.of("UTC");

        assertThat(tzService.resolveRelativeDate("today", utc)).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(tzService.resolveRelativeDate("today", kolkata)).isEqualTo(LocalDate.of(2026, 8, 25));
        assertThat(tzService.resolveRelativeDate("tomorrow", kolkata)).isEqualTo(LocalDate.of(2026, 8, 26));
    }

    @Test
    @DisplayName("Converts UTC instant to location time and back")
    void testInstantAndLocationConversion() {
        ZoneId kolkata = ZoneId.of("Asia/Kolkata");
        Instant utcInstant = Instant.parse("2026-08-24T09:30:00Z");

        ZonedDateTime locationTime = timezoneService.toLocationTime(utcInstant, kolkata);
        assertThat(locationTime.toLocalDate()).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(locationTime.toLocalTime()).isEqualTo(LocalTime.of(15, 0));

        Instant convertedBack = timezoneService.toUtcInstant(
                locationTime.toLocalDate(),
                locationTime.toLocalTime(),
                kolkata
        );
        assertThat(convertedBack).isEqualTo(utcInstant);
    }
}
