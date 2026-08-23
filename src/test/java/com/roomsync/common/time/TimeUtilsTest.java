package com.roomsync.common.time;

import com.roomsync.common.exception.InvalidTimeZoneException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeUtilsTest {

    @Test
    @DisplayName("Should validate IANA timezone identifiers correctly")
    void testTimeZoneValidation() {
        assertThat(TimeUtils.isValidZoneId("Asia/Kolkata")).isTrue();
        assertThat(TimeUtils.isValidZoneId("America/New_York")).isTrue();
        assertThat(TimeUtils.isValidZoneId("Europe/London")).isTrue();
        assertThat(TimeUtils.isValidZoneId("UTC")).isTrue();

        assertThat(TimeUtils.isValidZoneId(null)).isFalse();
        assertThat(TimeUtils.isValidZoneId("")).isFalse();
        assertThat(TimeUtils.isValidZoneId("Invalid/ZoneName")).isFalse();
        assertThat(TimeUtils.isValidZoneId("GMT+5:30")).isFalse();
    }

    @Test
    @DisplayName("Should throw InvalidTimeZoneException on invalid timezone")
    void testParseInvalidTimeZone() {
        assertThatThrownBy(() -> TimeUtils.parseZoneId("Mars/Base_One"))
                .isInstanceOf(InvalidTimeZoneException.class)
                .hasMessageContaining("Invalid IANA timezone identifier");
    }

    @Test
    @DisplayName("Should convert UTC Instant to Location ZonedDateTime and back")
    void testUtcAndLocationConversion() {
        Instant utcInstant = Instant.parse("2026-08-20T10:00:00Z");
        ZonedDateTime kolkataTime = TimeUtils.toLocationTime(utcInstant, "Asia/Kolkata");

        assertThat(kolkataTime.getZone().getId()).isEqualTo("Asia/Kolkata");
        assertThat(kolkataTime.getHour()).isEqualTo(15);
        assertThat(kolkataTime.getMinute()).isEqualTo(30);

        Instant roundTripped = TimeUtils.toUtcInstant(kolkataTime);
        assertThat(roundTripped).isEqualTo(utcInstant);
    }

    @Test
    @DisplayName("TimeRange: Should detect overlapping and non-overlapping intervals correctly")
    void testTimeRangeOverlaps() {
        Instant t1 = Instant.parse("2026-08-20T10:00:00Z");
        Instant t2 = Instant.parse("2026-08-20T11:00:00Z");
        Instant t3 = Instant.parse("2026-08-20T10:30:00Z");
        Instant t4 = Instant.parse("2026-08-20T11:30:00Z");
        Instant t5 = Instant.parse("2026-08-20T11:00:00Z");
        Instant t6 = Instant.parse("2026-08-20T12:00:00Z");

        TimeUtils.TimeRange r1 = new TimeUtils.TimeRange(t1, t2); // 10:00 - 11:00
        TimeUtils.TimeRange r2 = new TimeUtils.TimeRange(t3, t4); // 10:30 - 11:30
        TimeUtils.TimeRange r3 = new TimeUtils.TimeRange(t5, t6); // 11:00 - 12:00 (adjacent)

        assertThat(r1.overlaps(r2)).isTrue();
        assertThat(r2.overlaps(r1)).isTrue();
        assertThat(r1.overlaps(r3)).isFalse(); // Adjacent ranges do not overlap
        assertThat(r1.duration()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("DateTimeProvider: Should return deterministic time using fixed clock")
    void testDeterministicDateTimeProvider() {
        Instant fixedInstant = Instant.parse("2026-09-01T12:00:00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        DateTimeProvider provider = new SystemDateTimeProvider(fixedClock);

        assertThat(provider.now()).isEqualTo(fixedInstant);
        assertThat(provider.today(ZoneId.of("Asia/Kolkata"))).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(provider.currentTime(ZoneId.of("Asia/Kolkata"))).isEqualTo(LocalTime.of(17, 30));
    }
}
