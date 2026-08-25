package com.roomsync.common.time;

import com.roomsync.booking.exception.InvalidBookingTimeException;
import com.roomsync.common.exception.InvalidTimeZoneException;
import com.roomsync.location.entity.Location;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.zone.ZoneRules;
import java.util.List;
import java.util.Objects;

/**
 * Authoritative timezone service providing IANA timezone validation,
 * location timezone resolution, deterministic DST transition handling,
 * and location-aware relative date resolution.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TimezoneService {

    private final DateTimeProvider dateTimeProvider;

    /**
     * Validates whether a timezone identifier is a valid canonical IANA timezone.
     */
    public boolean isValidZoneId(String zoneId) {
        return TimeUtils.isValidZoneId(zoneId);
    }

    /**
     * Resolves a timezone identifier into a ZoneId, throwing InvalidTimeZoneException if invalid.
     */
    public ZoneId resolveZoneId(String zoneId) {
        return TimeUtils.parseZoneId(zoneId);
    }

    /**
     * Resolves the authoritative ZoneId for a physical Location entity.
     * The location's configured IANA timezone is the single source of truth.
     */
    public ZoneId getLocationZoneId(Location location) {
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null when resolving location timezone");
        }
        String timezone = location.getTimezone();
        if (timezone == null || timezone.trim().isEmpty()) {
            throw new InvalidTimeZoneException("Location timezone is not configured for location: " + location.getName());
        }
        return resolveZoneId(timezone);
    }

    /**
     * Converts a local date and time into an authoritative ZonedDateTime in the specified timezone,
     * applying deterministic DST transition rules:
     * - Nonexistent times (spring-forward gap) are rejected with InvalidBookingTimeException.
     * - Ambiguous times (fall-back overlap) are deterministically resolved to the earlier offset.
     */
    public ZonedDateTime toZonedDateTime(LocalDate date, LocalTime time, ZoneId zoneId) {
        Objects.requireNonNull(date, "Date cannot be null");
        Objects.requireNonNull(time, "Time cannot be null");
        Objects.requireNonNull(zoneId, "ZoneId cannot be null");

        LocalDateTime localDateTime = LocalDateTime.of(date, time);
        ZoneRules rules = zoneId.getRules();
        List<ZoneOffset> validOffsets = rules.getValidOffsets(localDateTime);

        // 1. Nonexistent DST time (Gap)
        if (validOffsets.isEmpty()) {
            log.warn("Nonexistent local time requested: {} in timezone {}", localDateTime, zoneId);
            throw new InvalidBookingTimeException(String.format(
                    "Requested local time '%s' does not exist in timezone '%s' due to daylight saving time transition",
                    localDateTime, zoneId.getId()
            ));
        }

        // 2. Ambiguous DST time (Overlap) -> Deterministic earlier offset
        if (validOffsets.size() > 1) {
            log.info("Ambiguous local time requested: {} in timezone {}. Resolving to earlier offset: {}",
                    localDateTime, zoneId, validOffsets.get(0));
            return ZonedDateTime.of(localDateTime, zoneId).withEarlierOffsetAtOverlap();
        }

        // 3. Normal 1:1 mapping
        return ZonedDateTime.of(localDateTime, zoneId);
    }

    /**
     * Converts local date and time to a UTC Instant.
     */
    public Instant toUtcInstant(LocalDate date, LocalTime time, ZoneId zoneId) {
        return toZonedDateTime(date, time, zoneId).toInstant();
    }

    /**
     * Converts a UTC Instant to a ZonedDateTime in the location timezone.
     */
    public ZonedDateTime toLocationTime(Instant instant, ZoneId zoneId) {
        Objects.requireNonNull(instant, "Instant cannot be null");
        Objects.requireNonNull(zoneId, "ZoneId cannot be null");
        return instant.atZone(zoneId);
    }

    /**
     * Converts a UTC Instant to a ZonedDateTime using a string timezone.
     */
    public ZonedDateTime toLocationTime(Instant instant, String zoneId) {
        return toLocationTime(instant, resolveZoneId(zoneId));
    }

    /**
     * Checks whether a given local date and time is nonexistent in the specified timezone due to a DST gap.
     */
    public boolean isNonexistentTime(LocalDate date, LocalTime time, ZoneId zoneId) {
        Objects.requireNonNull(date, "Date cannot be null");
        Objects.requireNonNull(time, "Time cannot be null");
        Objects.requireNonNull(zoneId, "ZoneId cannot be null");
        LocalDateTime localDateTime = LocalDateTime.of(date, time);
        return zoneId.getRules().getValidOffsets(localDateTime).isEmpty();
    }

    /**
     * Checks whether a given local date and time is ambiguous in the specified timezone due to a DST overlap.
     */
    public boolean isAmbiguousTime(LocalDate date, LocalTime time, ZoneId zoneId) {
        Objects.requireNonNull(date, "Date cannot be null");
        Objects.requireNonNull(time, "Time cannot be null");
        Objects.requireNonNull(zoneId, "ZoneId cannot be null");
        LocalDateTime localDateTime = LocalDateTime.of(date, time);
        return zoneId.getRules().getValidOffsets(localDateTime).size() > 1;
    }

    /**
     * Resolves a relative date string (e.g. "today", "tomorrow", or ISO date "YYYY-MM-DD")
     * into a LocalDate evaluated strictly within the location's authoritative timezone.
     */
    public LocalDate resolveRelativeDate(String relativeDate, ZoneId zoneId) {
        Objects.requireNonNull(zoneId, "ZoneId cannot be null");
        if (relativeDate == null || relativeDate.trim().isEmpty()) {
            throw new InvalidBookingTimeException("Relative date string cannot be null or blank");
        }

        String normalized = relativeDate.trim().toLowerCase();
        if ("today".equals(normalized)) {
            return dateTimeProvider.today(zoneId);
        }
        if ("tomorrow".equals(normalized)) {
            return dateTimeProvider.today(zoneId).plusDays(1);
        }

        try {
            return LocalDate.parse(relativeDate.trim());
        } catch (DateTimeParseException ex) {
            throw new InvalidBookingTimeException(String.format(
                    "Invalid relative date expression '%s'. Supported expressions: 'today', 'tomorrow', or ISO-8601 date (YYYY-MM-DD)",
                    relativeDate
            ));
        }
    }
}
