package com.roomsync.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Pluggable provider of current time and calendar values.
 * Allows deterministic clock injection for automated testing and simulation.
 */
public interface DateTimeProvider {

    Clock getClock();

    Instant now();

    OffsetDateTime nowOffsetDateTime();

    ZonedDateTime nowZonedDateTime(ZoneId zoneId);

    LocalDate today(ZoneId zoneId);

    LocalTime currentTime(ZoneId zoneId);
}
