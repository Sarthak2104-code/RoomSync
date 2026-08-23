package com.roomsync.common.time;

import com.roomsync.common.exception.InvalidTimeZoneException;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * Generic UTC and IANA time foundation utilities.
 */
public final class TimeUtils {

    private static final Set<String> AVAILABLE_ZONE_IDS = ZoneId.getAvailableZoneIds();

    private TimeUtils() {
        // Prevent instantiation
    }

    /**
     * Validates whether a given string is a valid canonical IANA timezone identifier (e.g. 'Asia/Kolkata', 'America/New_York', 'UTC').
     * Custom offset strings (like 'GMT+5:30' or 'UTC+05:30') are not valid IANA identifiers.
     */
    public static boolean isValidZoneId(String zoneId) {
        if (zoneId == null || zoneId.trim().isEmpty()) {
            return false;
        }
        String trimmed = zoneId.trim();
        return AVAILABLE_ZONE_IDS.contains(trimmed);
    }

    /**
     * Parses a string into a ZoneId, throwing InvalidTimeZoneException if invalid.
     */
    public static ZoneId parseZoneId(String zoneId) {
        if (!isValidZoneId(zoneId)) {
            throw new InvalidTimeZoneException(zoneId);
        }
        return ZoneId.of(zoneId.trim());
    }

    /**
     * Converts a UTC Instant to a ZonedDateTime in the specified location timezone.
     */
    public static ZonedDateTime toLocationTime(Instant instant, String zoneId) {
        Objects.requireNonNull(instant, "Instant cannot be null");
        ZoneId zone = parseZoneId(zoneId);
        return instant.atZone(zone);
    }

    /**
     * Converts a ZonedDateTime to a UTC Instant.
     */
    public static Instant toUtcInstant(ZonedDateTime zonedDateTime) {
        Objects.requireNonNull(zonedDateTime, "ZonedDateTime cannot be null");
        return zonedDateTime.toInstant();
    }

    /**
     * Converts an OffsetDateTime to a UTC Instant.
     */
    public static Instant toUtcInstant(OffsetDateTime offsetDateTime) {
        Objects.requireNonNull(offsetDateTime, "OffsetDateTime cannot be null");
        return offsetDateTime.toInstant();
    }

    /**
     * Generic, domain-agnostic time range value object.
     */
    public record TimeRange(Instant start, Instant end) {
        public TimeRange {
            Objects.requireNonNull(start, "Start time cannot be null");
            Objects.requireNonNull(end, "End time cannot be null");
            if (!end.isAfter(start)) {
                throw new IllegalArgumentException("End time must be strictly after start time");
            }
        }

        public boolean overlaps(TimeRange other) {
            Objects.requireNonNull(other, "Other TimeRange cannot be null");
            return this.start.isBefore(other.end) && other.start.isBefore(this.end);
        }

        public Duration duration() {
            return Duration.between(start, end);
        }
    }
}
