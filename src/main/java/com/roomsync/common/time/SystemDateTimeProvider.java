package com.roomsync.common.time;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Default production implementation of DateTimeProvider backed by UTC system clock.
 */
@Component
public class SystemDateTimeProvider implements DateTimeProvider {

    private final Clock clock;

    public SystemDateTimeProvider() {
        this.clock = Clock.systemUTC();
    }

    public SystemDateTimeProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Clock getClock() {
        return clock;
    }

    @Override
    public Instant now() {
        return clock.instant();
    }

    @Override
    public OffsetDateTime nowOffsetDateTime() {
        return OffsetDateTime.now(clock);
    }

    @Override
    public ZonedDateTime nowZonedDateTime(ZoneId zoneId) {
        return ZonedDateTime.now(clock.withZone(zoneId != null ? zoneId : ZoneOffset.UTC));
    }

    @Override
    public LocalDate today(ZoneId zoneId) {
        return LocalDate.now(clock.withZone(zoneId != null ? zoneId : ZoneOffset.UTC));
    }

    @Override
    public LocalTime currentTime(ZoneId zoneId) {
        return LocalTime.now(clock.withZone(zoneId != null ? zoneId : ZoneOffset.UTC));
    }
}
