package com.roomsync.common.time;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Immutable value object representing a validated, authoritative booking interval.
 * Encapsulates both UTC representations (for database storage and instant comparisons)
 * and location-zoned representations (for local business and calendar semantics).
 */
public record BookingInterval(
        Instant startInstant,
        Instant endInstant,
        OffsetDateTime startUtc,
        OffsetDateTime endUtc,
        ZonedDateTime startLocation,
        ZonedDateTime endLocation,
        LocalDate startDate,
        LocalDate endDate,
        Duration duration
) {
    public BookingInterval {
        Objects.requireNonNull(startInstant, "Start instant cannot be null");
        Objects.requireNonNull(endInstant, "End instant cannot be null");
        Objects.requireNonNull(startUtc, "Start UTC cannot be null");
        Objects.requireNonNull(endUtc, "End UTC cannot be null");
        Objects.requireNonNull(startLocation, "Start location time cannot be null");
        Objects.requireNonNull(endLocation, "End location time cannot be null");
        Objects.requireNonNull(startDate, "Start date cannot be null");
        Objects.requireNonNull(endDate, "End date cannot be null");
        Objects.requireNonNull(duration, "Duration cannot be null");
    }
}
