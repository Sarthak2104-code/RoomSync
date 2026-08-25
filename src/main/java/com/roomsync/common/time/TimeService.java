package com.roomsync.common.time;

import com.roomsync.booking.exception.InvalidBookingTimeException;
import com.roomsync.location.entity.Location;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Authoritative temporal business service for RoomSync.
 * Centralizes booking interval calculations, 15-minute boundary validation,
 * minimum duration validation, past-booking validation, and cross-midnight handling.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TimeService {

    public static final int BOUNDARY_MINUTES = 15;
    public static final int MINIMUM_DURATION_MINUTES = 15;

    private final TimezoneService timezoneService;
    private final DateTimeProvider dateTimeProvider;

    /**
     * Calculates and validates an authoritative BookingInterval from local date/time inputs
     * and a location's authoritative ZoneId.
     */
    public BookingInterval calculateBookingInterval(LocalDate date, LocalTime startTime, LocalTime endTime, ZoneId zoneId) {
        Objects.requireNonNull(date, "Local date cannot be null");
        Objects.requireNonNull(startTime, "Start time cannot be null");
        Objects.requireNonNull(endTime, "End time cannot be null");
        Objects.requireNonNull(zoneId, "ZoneId cannot be null");

        // 1. Enforce 15-minute boundary alignment on local start and end times
        validate15MinuteBoundary(startTime, "Booking start time");
        validate15MinuteBoundary(endTime, "Booking end time");

        // 2. Resolve cross-midnight bookings (when end time is earlier than start time, end is on following calendar date)
        LocalDate endDate;
        if (endTime.isBefore(startTime)) {
            endDate = date.plusDays(1);
            log.debug("Cross-midnight booking detected: start date {}, end date {}", date, endDate);
        } else {
            endDate = date;
        }

        // 3. Convert to location ZonedDateTime (enforces nonexistent DST gap rejection & deterministic overlap resolution)
        ZonedDateTime startZoned = timezoneService.toZonedDateTime(date, startTime, zoneId);
        ZonedDateTime endZoned = timezoneService.toZonedDateTime(endDate, endTime, zoneId);

        // 4. Extract authoritative UTC Instants
        Instant startInstant = startZoned.toInstant();
        Instant endInstant = endZoned.toInstant();

        // 5. Validate that end is strictly after start
        if (!endInstant.isAfter(startInstant)) {
            throw new InvalidBookingTimeException("Booking start time must be strictly before end time");
        }

        // 6. Enforce minimum duration of 15 minutes of actual elapsed time
        Duration duration = Duration.between(startInstant, endInstant);
        if (duration.toMinutes() < MINIMUM_DURATION_MINUTES) {
            throw new InvalidBookingTimeException(
                    String.format("Booking duration must be at least %d minutes", MINIMUM_DURATION_MINUTES)
            );
        }

        // 7. Validate that booking start time is not in the past
        validateNotPast(startInstant);

        // 8. Construct UTC OffsetDateTime representations for storage
        OffsetDateTime startUtc = startZoned.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();
        OffsetDateTime endUtc = endZoned.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();

        return new BookingInterval(
                startInstant,
                endInstant,
                startUtc,
                endUtc,
                startZoned,
                endZoned,
                date,
                endDate,
                duration
        );
    }

    /**
     * Calculates and validates a BookingInterval using the authoritative timezone from a Location entity.
     */
    public BookingInterval calculateBookingInterval(LocalDate date, LocalTime startTime, LocalTime endTime, Location location) {
        ZoneId zoneId = timezoneService.getLocationZoneId(location);
        return calculateBookingInterval(date, startTime, endTime, zoneId);
    }

    /**
     * Validates an OffsetDateTime interval against 15-minute boundaries, minimum duration,
     * chronological order, and past-time rules.
     */
    public void validateBookingTimes(OffsetDateTime startTime, OffsetDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new InvalidBookingTimeException("Start time and end time are required");
        }

        if (!startTime.isBefore(endTime)) {
            throw new InvalidBookingTimeException("Start time must be strictly before end time");
        }

        validate15MinuteBoundary(startTime, "Booking start time");
        validate15MinuteBoundary(endTime, "Booking end time");

        Duration duration = Duration.between(startTime, endTime);
        if (duration.toMinutes() < MINIMUM_DURATION_MINUTES) {
            throw new InvalidBookingTimeException(
                    String.format("Booking duration must be at least %d minutes", MINIMUM_DURATION_MINUTES)
            );
        }

        validateNotPast(startTime.toInstant());
    }

    /**
     * Validates that a LocalTime aligns to a 15-minute boundary (minute % 15 == 0, second == 0, nano == 0).
     */
    public void validate15MinuteBoundary(LocalTime time, String fieldName) {
        Objects.requireNonNull(time, fieldName + " cannot be null");
        if (time.getMinute() % BOUNDARY_MINUTES != 0 || time.getSecond() != 0 || time.getNano() != 0) {
            throw new InvalidBookingTimeException(
                    String.format("%s must be aligned to a %d-minute boundary with zero seconds and nanoseconds",
                            fieldName, BOUNDARY_MINUTES)
            );
        }
    }

    /**
     * Validates that an OffsetDateTime aligns to a 15-minute boundary.
     */
    public void validate15MinuteBoundary(OffsetDateTime dateTime, String fieldName) {
        Objects.requireNonNull(dateTime, fieldName + " cannot be null");
        if (dateTime.getMinute() % BOUNDARY_MINUTES != 0 || dateTime.getSecond() != 0 || dateTime.getNano() != 0) {
            throw new InvalidBookingTimeException(
                    String.format("%s must be aligned to a %d-minute boundary with zero seconds and nanoseconds",
                            fieldName, BOUNDARY_MINUTES)
            );
        }
    }

    /**
     * Validates that a booking start instant is not in the past relative to the authoritative clock.
     */
    public void validateNotPast(Instant startInstant) {
        Objects.requireNonNull(startInstant, "Start instant cannot be null");
        Instant now = dateTimeProvider.now();
        if (startInstant.isBefore(now)) {
            throw new InvalidBookingTimeException("Booking start time cannot be in the past");
        }
    }

    /**
     * Converts a location-zoned date/time to a UTC OffsetDateTime.
     */
    public OffsetDateTime toUtcOffsetDateTime(ZonedDateTime zonedDateTime) {
        Objects.requireNonNull(zonedDateTime, "ZonedDateTime cannot be null");
        return zonedDateTime.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();
    }

    /**
     * Converts a UTC Instant to a ZonedDateTime in the specified location zone.
     */
    public ZonedDateTime toLocationZonedDateTime(Instant instant, ZoneId zoneId) {
        return timezoneService.toLocationTime(instant, zoneId);
    }
}
