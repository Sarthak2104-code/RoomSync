package com.roomsync.common.time;

import com.roomsync.booking.exception.InvalidBookingTimeException;
import com.roomsync.common.exception.ErrorCode;
import com.roomsync.location.entity.Location;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeServiceTest {

    private DateTimeProvider dateTimeProvider;
    private TimezoneService timezoneService;
    private TimeService timeService;

    // Fixed instant: 2026-08-24 08:00:00 UTC (13:30:00 Asia/Kolkata)
    private final Instant fixedNow = Instant.parse("2026-08-24T08:00:00Z");

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);
        dateTimeProvider = new SystemDateTimeProvider(fixedClock);
        timezoneService = new TimezoneService(dateTimeProvider);
        timeService = new TimeService(timezoneService, dateTimeProvider);
    }

    @Test
    @DisplayName("Test 1: Normal booking 15:00 -> 16:00 calculates correct interval and UTC timestamps")
    void testNormalBooking() {
        LocalDate date = LocalDate.of(2026, 8, 24);
        LocalTime startTime = LocalTime.of(15, 0);
        LocalTime endTime = LocalTime.of(16, 0);
        ZoneId kolkataZone = ZoneId.of("Asia/Kolkata");

        BookingInterval interval = timeService.calculateBookingInterval(date, startTime, endTime, kolkataZone);

        assertThat(interval).isNotNull();
        assertThat(interval.duration()).isEqualTo(Duration.ofMinutes(60));
        assertThat(interval.startDate()).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(interval.endDate()).isEqualTo(LocalDate.of(2026, 8, 24));

        // 15:00 IST (+05:30) is 09:30 UTC
        // 16:00 IST (+05:30) is 10:30 UTC
        assertThat(interval.startInstant()).isEqualTo(Instant.parse("2026-08-24T09:30:00Z"));
        assertThat(interval.endInstant()).isEqualTo(Instant.parse("2026-08-24T10:30:00Z"));
        assertThat(interval.startUtc()).isEqualTo(OffsetDateTime.parse("2026-08-24T09:30:00Z"));
        assertThat(interval.endUtc()).isEqualTo(OffsetDateTime.parse("2026-08-24T10:30:00Z"));
    }

    @Test
    @DisplayName("Test 2: Cross-midnight booking 23:30 -> 00:30 is interpreted on following calendar date")
    void testCrossMidnightBooking() {
        LocalDate date = LocalDate.of(2026, 8, 24);
        LocalTime startTime = LocalTime.of(23, 30);
        LocalTime endTime = LocalTime.of(0, 30);
        ZoneId kolkataZone = ZoneId.of("Asia/Kolkata");

        BookingInterval interval = timeService.calculateBookingInterval(date, startTime, endTime, kolkataZone);

        assertThat(interval).isNotNull();
        assertThat(interval.duration()).isEqualTo(Duration.ofMinutes(60));
        assertThat(interval.startDate()).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(interval.endDate()).isEqualTo(LocalDate.of(2026, 8, 25));

        // 2026-08-24 23:30 IST is 2026-08-24 18:00 UTC
        // 2026-08-25 00:30 IST is 2026-08-24 19:00 UTC
        assertThat(interval.startInstant()).isEqualTo(Instant.parse("2026-08-24T18:00:00Z"));
        assertThat(interval.endInstant()).isEqualTo(Instant.parse("2026-08-24T19:00:00Z"));
    }

    @Test
    @DisplayName("Test 3: Invalid 15-minute boundary throws INVALID_TIME error")
    void testInvalid15MinuteBoundary() {
        LocalDate date = LocalDate.of(2026, 8, 24);
        ZoneId kolkataZone = ZoneId.of("Asia/Kolkata");

        // Start time 15:10 is not on 15-minute boundary
        assertThatThrownBy(() -> timeService.calculateBookingInterval(
                date, LocalTime.of(15, 10), LocalTime.of(16, 0), kolkataZone
        ))
                .isInstanceOf(InvalidBookingTimeException.class)
                .satisfies(ex -> assertThat(((InvalidBookingTimeException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_TIME))
                .hasMessageContaining("must be aligned to a 15-minute boundary");

        // End time 16:20 is not on 15-minute boundary
        assertThatThrownBy(() -> timeService.calculateBookingInterval(
                date, LocalTime.of(15, 0), LocalTime.of(16, 20), kolkataZone
        ))
                .isInstanceOf(InvalidBookingTimeException.class)
                .satisfies(ex -> assertThat(((InvalidBookingTimeException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_TIME))
                .hasMessageContaining("must be aligned to a 15-minute boundary");
    }

    @Test
    @DisplayName("Test 4: Less than minimum 15-minute duration throws INVALID_TIME error")
    void testLessThanMinimumDuration() {
        LocalDate date = LocalDate.of(2026, 8, 24);
        ZoneId kolkataZone = ZoneId.of("Asia/Kolkata");

        // 15:00 to 15:00 (0 minutes)
        assertThatThrownBy(() -> timeService.calculateBookingInterval(
                date, LocalTime.of(15, 0), LocalTime.of(15, 0), kolkataZone
        ))
                .isInstanceOf(InvalidBookingTimeException.class)
                .satisfies(ex -> assertThat(((InvalidBookingTimeException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_TIME));
    }

    @Test
    @DisplayName("Test 5: Past booking start time throws INVALID_TIME error")
    void testPastBookingRejected() {
        // fixedNow is 2026-08-24 08:00:00 UTC (13:30:00 Asia/Kolkata)
        // A booking at 10:00:00 Asia/Kolkata is in the past
        LocalDate date = LocalDate.of(2026, 8, 24);
        LocalTime pastStart = LocalTime.of(10, 0);
        LocalTime pastEnd = LocalTime.of(11, 0);
        ZoneId kolkataZone = ZoneId.of("Asia/Kolkata");

        assertThatThrownBy(() -> timeService.calculateBookingInterval(date, pastStart, pastEnd, kolkataZone))
                .isInstanceOf(InvalidBookingTimeException.class)
                .satisfies(ex -> assertThat(((InvalidBookingTimeException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_TIME))
                .hasMessageContaining("cannot be in the past");
    }

    @Test
    @DisplayName("Test 6: UTC date changes while local date does not (Early morning Asia/Kolkata booking)")
    void testUtcDateShiftVsLocalDate() {
        // Test date: 2026-08-25
        // Local: 00:15 -> 01:15 in Asia/Kolkata (+05:30)
        // UTC: 2026-08-24 18:45:00Z -> 2026-08-24 19:45:00Z
        // Local calendar date is 2026-08-25, but UTC date is 2026-08-24
        LocalDate date = LocalDate.of(2026, 8, 25);
        LocalTime startTime = LocalTime.of(0, 15);
        LocalTime endTime = LocalTime.of(1, 15);
        ZoneId kolkataZone = ZoneId.of("Asia/Kolkata");

        BookingInterval interval = timeService.calculateBookingInterval(date, startTime, endTime, kolkataZone);

        assertThat(interval).isNotNull();
        assertThat(interval.startDate()).isEqualTo(LocalDate.of(2026, 8, 25));
        assertThat(interval.endDate()).isEqualTo(LocalDate.of(2026, 8, 25));

        // Start instant is on previous UTC date (2026-08-24)
        assertThat(interval.startInstant()).isEqualTo(Instant.parse("2026-08-24T18:45:00Z"));
        assertThat(interval.endInstant()).isEqualTo(Instant.parse("2026-08-24T19:45:00Z"));
        assertThat(interval.startUtc().toLocalDate()).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(interval.startLocation().toLocalDate()).isEqualTo(LocalDate.of(2026, 8, 25));
    }

    @Test
    @DisplayName("Calculates booking interval from Location entity authority")
    void testCalculateFromLocationEntity() {
        Location location = Location.builder()
                .id(1L)
                .name("New York Branch")
                .code("NYC")
                .timezone("America/New_York")
                .build();

        LocalDate date = LocalDate.of(2026, 8, 24);
        LocalTime startTime = LocalTime.of(10, 0);
        LocalTime endTime = LocalTime.of(11, 30);

        BookingInterval interval = timeService.calculateBookingInterval(date, startTime, endTime, location);

        assertThat(interval).isNotNull();
        assertThat(interval.duration()).isEqualTo(Duration.ofMinutes(90));
        // 10:00 EDT (-04:00) is 14:00 UTC
        assertThat(interval.startInstant()).isEqualTo(Instant.parse("2026-08-24T14:00:00Z"));
        assertThat(interval.endInstant()).isEqualTo(Instant.parse("2026-08-24T15:30:00Z"));
    }

    @Test
    @DisplayName("Validates OffsetDateTime interval directly")
    void testValidateOffsetDateTimeInterval() {
        OffsetDateTime start = OffsetDateTime.parse("2026-08-24T15:00:00Z");
        OffsetDateTime end = OffsetDateTime.parse("2026-08-24T16:00:00Z");

        timeService.validateBookingTimes(start, end);

        // Invalid: end before start
        assertThatThrownBy(() -> timeService.validateBookingTimes(end, start))
                .isInstanceOf(InvalidBookingTimeException.class)
                .hasMessageContaining("strictly before end time");

        // Invalid: duration under 15 minutes
        OffsetDateTime shortEnd = OffsetDateTime.parse("2026-08-24T15:05:00Z");
        assertThatThrownBy(() -> timeService.validateBookingTimes(start, shortEnd))
                .isInstanceOf(InvalidBookingTimeException.class);
    }
}
