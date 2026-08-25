package com.roomsync.booking.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingConcurrencyServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private BookingConcurrencyService bookingConcurrencyService;

    @BeforeEach
    void setUp() {
        bookingConcurrencyService = new BookingConcurrencyService(jdbcTemplate);
    }

    @Test
    @DisplayName("Lock key generation is deterministic for same (roomId, date)")
    void testLockKeyGenerationIsDeterministic() {
        Long roomId = 5L;
        LocalDate date = LocalDate.of(2026, 8, 21);

        long key1 = bookingConcurrencyService.generateLockKey(roomId, date);
        long key2 = bookingConcurrencyService.generateLockKey(roomId, date);

        assertThat(key1).isEqualTo(key2);
    }

    @Test
    @DisplayName("Different rooms on same date produce different lock keys")
    void testDifferentRoomsProduceDifferentKeys() {
        LocalDate date = LocalDate.of(2026, 8, 21);

        long keyRoomA = bookingConcurrencyService.generateLockKey(1L, date);
        long keyRoomB = bookingConcurrencyService.generateLockKey(2L, date);

        assertThat(keyRoomA).isNotEqualTo(keyRoomB);
    }

    @Test
    @DisplayName("Same room on different dates produces different lock keys")
    void testDifferentDatesProduceDifferentKeys() {
        Long roomId = 1L;
        LocalDate date1 = LocalDate.of(2026, 8, 21);
        LocalDate date2 = LocalDate.of(2026, 8, 22);

        long keyDate1 = bookingConcurrencyService.generateLockKey(roomId, date1);
        long keyDate2 = bookingConcurrencyService.generateLockKey(roomId, date2);

        assertThat(keyDate1).isNotEqualTo(keyDate2);
    }

    @Test
    @DisplayName("Calculates single affected date for normal same-day booking")
    void testCalculateAffectedDatesSameDay() {
        ZoneId kolkataZone = ZoneId.of("Asia/Kolkata");
        // 15:00 to 16:00 IST (+05:30) on 2026-08-21 is 09:30 to 10:30 UTC
        OffsetDateTime start = OffsetDateTime.parse("2026-08-21T09:30:00Z");
        OffsetDateTime end = OffsetDateTime.parse("2026-08-21T10:30:00Z");

        List<LocalDate> dates = bookingConcurrencyService.calculateAffectedLocalDates(start, end, kolkataZone);

        assertThat(dates).containsExactly(LocalDate.of(2026, 8, 21));
    }

    @Test
    @DisplayName("Calculates multiple affected dates for cross-midnight booking")
    void testCalculateAffectedDatesCrossMidnight() {
        ZoneId kolkataZone = ZoneId.of("Asia/Kolkata");
        // 23:30 IST on 2026-08-21 to 00:30 IST on 2026-08-22
        // UTC: 2026-08-21T18:00:00Z to 2026-08-21T19:00:00Z
        OffsetDateTime start = OffsetDateTime.parse("2026-08-21T18:00:00Z");
        OffsetDateTime end = OffsetDateTime.parse("2026-08-21T19:00:00Z");

        List<LocalDate> dates = bookingConcurrencyService.calculateAffectedLocalDates(start, end, kolkataZone);

        assertThat(dates).containsExactly(
                LocalDate.of(2026, 8, 21),
                LocalDate.of(2026, 8, 22)
        );
    }

    @Test
    @DisplayName("Booking ending exactly at local midnight does not include following day")
    void testCalculateAffectedDatesEndingAtMidnight() {
        ZoneId kolkataZone = ZoneId.of("Asia/Kolkata");
        // 23:00 IST on 2026-08-21 to 00:00 IST on 2026-08-22 (exact midnight)
        // UTC: 2026-08-21T17:30:00Z to 2026-08-21T18:30:00Z
        OffsetDateTime start = OffsetDateTime.parse("2026-08-21T17:30:00Z");
        OffsetDateTime end = OffsetDateTime.parse("2026-08-21T18:30:00Z");

        List<LocalDate> dates = bookingConcurrencyService.calculateAffectedLocalDates(start, end, kolkataZone);

        assertThat(dates).containsExactly(LocalDate.of(2026, 8, 21));
    }

    @Test
    @DisplayName("Generates lock keys in deterministic ascending numerical order")
    void testDeterministicLockKeyOrdering() {
        Long roomId = 10L;
        LocalDate d1 = LocalDate.of(2026, 8, 21);
        LocalDate d2 = LocalDate.of(2026, 8, 22);
        LocalDate d3 = LocalDate.of(2026, 8, 20);

        List<Long> keysForward = bookingConcurrencyService.generateDeterministicLockKeys(roomId, List.of(d1, d2, d3));
        List<Long> keysReverse = bookingConcurrencyService.generateDeterministicLockKeys(roomId, List.of(d3, d2, d1));

        assertThat(keysForward).isEqualTo(keysReverse);
        assertThat(keysForward).isSorted();
    }

    @Test
    @DisplayName("Acquires PostgreSQL advisory locks sequentially")
    void testAcquireAdvisoryLocks() {
        List<Long> keys = List.of(12345L, 67890L);

        bookingConcurrencyService.acquireAdvisoryLocks(keys);

        verify(jdbcTemplate).queryForList("SELECT pg_advisory_xact_lock(?)", 12345L);
        verify(jdbcTemplate).queryForList("SELECT pg_advisory_xact_lock(?)", 67890L);
    }

    @Test
    @DisplayName("Combines and sorts lock keys across old and new rooms and dates for reschedule")
    void testAcquireLocksForReschedule() {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        OffsetDateTime oldStart = OffsetDateTime.parse("2026-08-21T09:30:00Z");
        OffsetDateTime oldEnd = OffsetDateTime.parse("2026-08-21T10:30:00Z");

        OffsetDateTime newStart = OffsetDateTime.parse("2026-08-22T09:30:00Z");
        OffsetDateTime newEnd = OffsetDateTime.parse("2026-08-22T10:30:00Z");

        List<Long> keys = bookingConcurrencyService.acquireLocksForReschedule(
                1L, oldStart, oldEnd, zone,
                2L, newStart, newEnd, zone
        );

        assertThat(keys).hasSize(2);
        assertThat(keys).isSorted();
    }
}
