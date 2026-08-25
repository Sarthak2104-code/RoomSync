package com.roomsync.booking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Concurrency coordination service for RoomSync bookings.
 * Responsible for calculating location-local affected calendar dates,
 * generating deterministic 64-bit advisory lock keys for (room_id, local_date),
 * sorting lock keys to prevent deadlocks, and acquiring transaction-scoped
 * PostgreSQL advisory locks (pg_advisory_xact_lock).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingConcurrencyService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Calculates all location-local calendar dates spanned by a booking interval [start, end).
     * Normal same-day bookings return a single date (e.g. [2026-08-21]).
     * Cross-midnight bookings return both dates (e.g. [2026-08-21, 2026-08-22]).
     */
    public List<LocalDate> calculateAffectedLocalDates(OffsetDateTime startTime, OffsetDateTime endTime, ZoneId zoneId) {
        Objects.requireNonNull(startTime, "Start time cannot be null");
        Objects.requireNonNull(endTime, "End time cannot be null");
        Objects.requireNonNull(zoneId, "ZoneId cannot be null");

        ZonedDateTime startZoned = startTime.atZoneSameInstant(zoneId);
        ZonedDateTime endZoned = endTime.atZoneSameInstant(zoneId);

        LocalDate startDate = startZoned.toLocalDate();
        LocalDate endDate = endZoned.toLocalDate();

        // If the booking ends exactly at local midnight (00:00:00) on a subsequent day,
        // the half-open interval [start, end) does not extend into that subsequent day.
        if (endZoned.toLocalTime().equals(LocalTime.MIDNIGHT) && endDate.isAfter(startDate)) {
            endDate = endDate.minusDays(1);
        }

        return startDate.datesUntil(endDate.plusDays(1)).toList();
    }

    /**
     * Generates a deterministic 64-bit advisory lock key from the complete (room_id, local_date) identity
     * using a high-dispersion 64-bit mixing function.
     * The same (room_id, local_date) pair always yields the exact same key across JVM instances and threads.
     */
    public long generateLockKey(Long roomId, LocalDate localDate) {
        Objects.requireNonNull(roomId, "Room ID cannot be null");
        Objects.requireNonNull(localDate, "LocalDate cannot be null");

        long k1 = roomId;
        long k2 = localDate.toEpochDay();

        // 64-bit high-dispersion mix (Murmur3/SplitMix64 mix)
        long h = k1 * 0x9e3779b97f4a7c15L + k2 * 0xbf58476d1ce4e5b9L;
        h ^= (h >>> 30);
        h *= 0xbf58476d1ce4e5b9L;
        h ^= (h >>> 27);
        h *= 0x94d049bb133111ebL;
        h ^= (h >>> 31);
        return h;
    }

    /**
     * Generates deterministic advisory lock keys for all specified local dates and sorts them
     * in ascending numerical order to prevent deadlocks during concurrent multi-date acquisitions.
     */
    public List<Long> generateDeterministicLockKeys(Long roomId, List<LocalDate> localDates) {
        Objects.requireNonNull(roomId, "Room ID cannot be null");
        Objects.requireNonNull(localDates, "LocalDates list cannot be null");

        return localDates.stream()
                .distinct()
                .map(date -> generateLockKey(roomId, date))
                .sorted()
                .toList();
    }

    /**
     * Acquires PostgreSQL transaction-scoped advisory locks (pg_advisory_xact_lock) for each key
     * in strictly sorted order. The locks belong to the current database transaction and automatically
     * release upon commit or rollback.
     */
    public void acquireAdvisoryLocks(List<Long> sortedLockKeys) {
        Objects.requireNonNull(sortedLockKeys, "Lock keys list cannot be null");

        for (Long key : sortedLockKeys) {
            log.debug("Acquiring PostgreSQL transaction-scoped advisory lock for key: {}", key);
            jdbcTemplate.queryForList("SELECT pg_advisory_xact_lock(?)", key);
        }
    }

    /**
     * Orchestrates the complete advisory locking process for a booking request:
     * 1. Determines affected location-local calendar dates
     * 2. Generates deterministic, sorted lock keys
     * 3. Acquires transaction-scoped PostgreSQL advisory locks
     */
    public List<Long> acquireLocksForBooking(Long roomId, OffsetDateTime startTime, OffsetDateTime endTime, ZoneId zoneId) {
        List<LocalDate> affectedDates = calculateAffectedLocalDates(startTime, endTime, zoneId);
        List<Long> sortedKeys = generateDeterministicLockKeys(roomId, affectedDates);
        acquireAdvisoryLocks(sortedKeys);
        return sortedKeys;
    }

    /**
     * Orchestrates the complete advisory locking process for a reschedule request:
     * 1. Determines affected location-local calendar dates for both OLD and NEW intervals
     * 2. Generates deterministic 64-bit lock keys across all (room_id, local_date) combinations
     * 3. Sorts all keys globally in ascending numerical order, eliminating duplicates
     * 4. Acquires transaction-scoped PostgreSQL advisory locks (pg_advisory_xact_lock) in sorted order
     */
    public List<Long> acquireLocksForReschedule(
            Long oldRoomId, OffsetDateTime oldStart, OffsetDateTime oldEnd, ZoneId oldZoneId,
            Long newRoomId, OffsetDateTime newStart, OffsetDateTime newEnd, ZoneId newZoneId) {
        Objects.requireNonNull(oldRoomId, "Old room ID cannot be null");
        Objects.requireNonNull(oldStart, "Old start time cannot be null");
        Objects.requireNonNull(oldEnd, "Old end time cannot be null");
        Objects.requireNonNull(oldZoneId, "Old ZoneId cannot be null");
        Objects.requireNonNull(newRoomId, "New room ID cannot be null");
        Objects.requireNonNull(newStart, "New start time cannot be null");
        Objects.requireNonNull(newEnd, "New end time cannot be null");
        Objects.requireNonNull(newZoneId, "New ZoneId cannot be null");

        List<LocalDate> oldDates = calculateAffectedLocalDates(oldStart, oldEnd, oldZoneId);
        List<LocalDate> newDates = calculateAffectedLocalDates(newStart, newEnd, newZoneId);

        Set<Long> combinedKeys = new HashSet<>();
        for (LocalDate date : oldDates) {
            combinedKeys.add(generateLockKey(oldRoomId, date));
        }
        for (LocalDate date : newDates) {
            combinedKeys.add(generateLockKey(newRoomId, date));
        }

        List<Long> sortedKeys = combinedKeys.stream().sorted().toList();
        acquireAdvisoryLocks(sortedKeys);
        return sortedKeys;
    }
}
