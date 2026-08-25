package com.roomsync.booking.service;

import com.roomsync.booking.dto.RecurringOccurrencePreview;
import com.roomsync.booking.repository.BookingRepository;
import com.roomsync.common.time.BookingInterval;
import com.roomsync.common.time.TimeService;
import com.roomsync.common.time.TimezoneService;
import com.roomsync.location.entity.Location;
import com.roomsync.room.entity.Room;
import com.roomsync.room.entity.RoomStatus;
import com.roomsync.room.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecurringConflictServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private TimezoneService timezoneService;

    @Mock
    private TimeService timeService;

    @InjectMocks
    private RecurringConflictService recurringConflictService;

    private Room room;
    private Location location;
    private ZoneId zoneId;

    @BeforeEach
    void setUp() {
        location = Location.builder()
                .id(1L)
                .name("Mumbai HQ")
                .timezone("Asia/Kolkata")
                .active(true)
                .build();

        room = Room.builder()
                .id(10L)
                .name("Taj Mahal")
                .location(location)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build();

        zoneId = ZoneId.of("Asia/Kolkata");
    }

    @Test
    @DisplayName("Evaluates available and conflicting occurrences correctly")
    void testEvaluateOccurrences() {
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room));
        when(timezoneService.getLocationZoneId(location)).thenReturn(zoneId);

        LocalDate d1 = LocalDate.of(2026, 9, 1);
        LocalDate d2 = LocalDate.of(2026, 9, 2);

        ZonedDateTime startZ1 = ZonedDateTime.of(d1, LocalTime.of(10, 0), zoneId);
        ZonedDateTime endZ1 = ZonedDateTime.of(d1, LocalTime.of(11, 0), zoneId);
        OffsetDateTime start1 = startZ1.toOffsetDateTime();
        OffsetDateTime end1 = endZ1.toOffsetDateTime();
        BookingInterval interval1 = new BookingInterval(start1.toInstant(), end1.toInstant(), start1, end1, startZ1, endZ1, d1, d1, Duration.ofHours(1));

        ZonedDateTime startZ2 = ZonedDateTime.of(d2, LocalTime.of(10, 0), zoneId);
        ZonedDateTime endZ2 = ZonedDateTime.of(d2, LocalTime.of(11, 0), zoneId);
        OffsetDateTime start2 = startZ2.toOffsetDateTime();
        OffsetDateTime end2 = endZ2.toOffsetDateTime();
        BookingInterval interval2 = new BookingInterval(start2.toInstant(), end2.toInstant(), start2, end2, startZ2, endZ2, d2, d2, Duration.ofHours(1));

        when(timeService.calculateBookingInterval(eq(d1), eq(LocalTime.of(10, 0)), eq(LocalTime.of(11, 0)), eq(zoneId)))
                .thenReturn(interval1);
        when(timeService.calculateBookingInterval(eq(d2), eq(LocalTime.of(10, 0)), eq(LocalTime.of(11, 0)), eq(zoneId)))
                .thenReturn(interval2);

        // Occurrence 1 has no overlap (AVAILABLE), Occurrence 2 has overlap (CONFLICT)
        when(bookingRepository.existsOverlappingBooking(10L, start1, end1)).thenReturn(false);
        when(bookingRepository.existsOverlappingBooking(10L, start2, end2)).thenReturn(true);

        List<RecurrenceGenerator.OccurrenceDate> occurrenceDates = List.of(
                new RecurrenceGenerator.OccurrenceDate(1, d1),
                new RecurrenceGenerator.OccurrenceDate(2, d2)
        );

        List<RecurringOccurrencePreview> previews = recurringConflictService.evaluateOccurrences(
                10L,
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                occurrenceDates
        );

        assertThat(previews).hasSize(2);
        assertThat(previews.get(0).getAvailability()).isEqualTo("AVAILABLE");
        assertThat(previews.get(0).getConflictReason()).isNull();

        assertThat(previews.get(1).getAvailability()).isEqualTo("CONFLICT");
        assertThat(previews.get(1).getConflictReason()).contains("overlaps with an existing confirmed booking");
    }

    @Test
    @DisplayName("Returns conflict when room is locked")
    void testLockedRoomConflict() {
        room.setStatus(RoomStatus.LOCKED);
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room));
        when(timezoneService.getLocationZoneId(location)).thenReturn(zoneId);

        List<RecurringOccurrencePreview> previews = recurringConflictService.evaluateOccurrences(
                10L,
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                List.of(new RecurrenceGenerator.OccurrenceDate(1, LocalDate.of(2026, 9, 1)))
        );

        assertThat(previews).hasSize(1);
        assertThat(previews.get(0).getAvailability()).isEqualTo("CONFLICT");
        assertThat(previews.get(0).getConflictReason()).contains("currently locked");
    }
}
