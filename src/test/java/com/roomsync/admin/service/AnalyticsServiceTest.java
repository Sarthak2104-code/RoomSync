package com.roomsync.admin.service;

import com.roomsync.admin.dto.RoomUtilizationResponse;
import com.roomsync.admin.dto.UtilizationReportResponse;
import com.roomsync.booking.entity.Booking;
import com.roomsync.booking.entity.BookingStatus;
import com.roomsync.booking.repository.BookingRepository;
import com.roomsync.common.time.DateTimeProvider;
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

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private TimezoneService timezoneService;

    @Mock
    private DateTimeProvider dateTimeProvider;

    @InjectMocks
    private AnalyticsService analyticsService;

    private Location locationMumbai;
    private Location locationLondon;
    private Room roomAlpha;
    private Room roomBeta;

    @BeforeEach
    void setUp() {
        locationMumbai = Location.builder()
                .id(1L)
                .name("Mumbai HQ")
                .timezone("Asia/Kolkata")
                .active(true)
                .build();

        locationLondon = Location.builder()
                .id(2L)
                .name("London Office")
                .timezone("Europe/London")
                .active(true)
                .build();

        roomAlpha = Room.builder()
                .id(10L)
                .name("Room Alpha")
                .location(locationMumbai)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build();

        roomBeta = Room.builder()
                .id(20L)
                .name("Room Beta")
                .location(locationLondon)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("1 & 7 & 10. Single-day utilization with CONFIRMED bookings calculates accurate percentage and rounding")
    void testSingleDayUtilization() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        ZoneId zoneId = ZoneId.of("Asia/Kolkata");

        when(roomRepository.findById(10L)).thenReturn(Optional.of(roomAlpha));
        when(timezoneService.getLocationZoneId(locationMumbai)).thenReturn(zoneId);

        // 1 day = 1440 available minutes
        // Booking 1: 10:00 to 12:00 IST (120 mins) -> 04:30 to 06:30 UTC
        OffsetDateTime b1Start = ZonedDateTime.of(date, java.time.LocalTime.of(10, 0), zoneId).toOffsetDateTime();
        OffsetDateTime b1End = ZonedDateTime.of(date, java.time.LocalTime.of(12, 0), zoneId).toOffsetDateTime();
        Booking b1 = Booking.builder()
                .id(1L)
                .room(roomAlpha)
                .startTime(b1Start)
                .endTime(b1End)
                .status(BookingStatus.CONFIRMED)
                .build();

        // Booking 2: 14:00 to 15:30 IST (90 mins) -> 08:30 to 10:00 UTC
        OffsetDateTime b2Start = ZonedDateTime.of(date, java.time.LocalTime.of(14, 0), zoneId).toOffsetDateTime();
        OffsetDateTime b2End = ZonedDateTime.of(date, java.time.LocalTime.of(15, 30), zoneId).toOffsetDateTime();
        Booking b2 = Booking.builder()
                .id(2L)
                .room(roomAlpha)
                .startTime(b2Start)
                .endTime(b2End)
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findEffectiveBookingsForRoomInInterval(eq(10L), any(), any()))
                .thenReturn(List.of(b1, b2));

        UtilizationReportResponse report = analyticsService.getUtilization(date, date, null, 10L);

        assertThat(report.getRooms()).hasSize(1);
        RoomUtilizationResponse roomReport = report.getRooms().get(0);
        assertThat(roomReport.getTotalAvailableMinutes()).isEqualTo(1440L);
        assertThat(roomReport.getTotalBookedMinutes()).isEqualTo(210L); // 120 + 90
        // (210 / 1440) * 100 = 14.58333... -> 14.58%
        assertThat(roomReport.getUtilizationPercentage()).isEqualTo(14.58);
        assertThat(roomReport.getBookingCount()).isEqualTo(2);
        assertThat(report.getOverallUtilizationPercentage()).isEqualTo(14.58);
    }

    @Test
    @DisplayName("2 & 8 & 9. Multi-day utilization with COMPLETED bookings and reporting window clamping")
    void testMultiDayUtilizationWithClamping() {
        LocalDate startDate = LocalDate.of(2026, 9, 1);
        LocalDate endDate = LocalDate.of(2026, 9, 2); // 2 days = 2880 mins
        ZoneId zoneId = ZoneId.of("Asia/Kolkata");

        when(roomRepository.findById(10L)).thenReturn(Optional.of(roomAlpha));
        when(timezoneService.getLocationZoneId(locationMumbai)).thenReturn(zoneId);

        OffsetDateTime windowStartUtc = startDate.atStartOfDay(zoneId).toOffsetDateTime();
        OffsetDateTime windowEndUtc = endDate.plusDays(1).atStartOfDay(zoneId).toOffsetDateTime();

        // Booking starting 1 hour BEFORE windowStartUtc and ending 2 hours into window
        // Total duration: 3 hours, clamped inside window: 2 hours (120 mins)
        Booking bBefore = Booking.builder()
                .id(1L)
                .room(roomAlpha)
                .startTime(windowStartUtc.minusHours(1))
                .endTime(windowStartUtc.plusHours(2))
                .status(BookingStatus.COMPLETED)
                .build();

        // Booking starting 2 hours before windowEndUtc and ending 1 hour AFTER windowEndUtc
        // Total duration: 3 hours, clamped inside window: 2 hours (120 mins)
        Booking bAfter = Booking.builder()
                .id(2L)
                .room(roomAlpha)
                .startTime(windowEndUtc.minusHours(2))
                .endTime(windowEndUtc.plusHours(1))
                .status(BookingStatus.COMPLETED)
                .build();

        when(bookingRepository.findEffectiveBookingsForRoomInInterval(eq(10L), eq(windowStartUtc), eq(windowEndUtc)))
                .thenReturn(List.of(bBefore, bAfter));

        UtilizationReportResponse report = analyticsService.getUtilization(startDate, endDate, null, 10L);

        RoomUtilizationResponse roomReport = report.getRooms().get(0);
        assertThat(roomReport.getTotalAvailableMinutes()).isEqualTo(2880L);
        assertThat(roomReport.getTotalBookedMinutes()).isEqualTo(240L); // 120 + 120
        // (240 / 2880) * 100 = 8.333... -> 8.33%
        assertThat(roomReport.getUtilizationPercentage()).isEqualTo(8.33);
    }

    @Test
    @DisplayName("3. Cross-midnight booking correctly counts total duration across days")
    void testCrossMidnightBookingUtilization() {
        LocalDate startDate = LocalDate.of(2026, 9, 1);
        LocalDate endDate = LocalDate.of(2026, 9, 2);
        ZoneId zoneId = ZoneId.of("Asia/Kolkata");

        when(roomRepository.findById(10L)).thenReturn(Optional.of(roomAlpha));
        when(timezoneService.getLocationZoneId(locationMumbai)).thenReturn(zoneId);

        // Booking from 23:00 on Sept 1 to 02:00 on Sept 2 (3 hours = 180 mins)
        OffsetDateTime crossStart = ZonedDateTime.of(LocalDate.of(2026, 9, 1), java.time.LocalTime.of(23, 0), zoneId).toOffsetDateTime();
        OffsetDateTime crossEnd = ZonedDateTime.of(LocalDate.of(2026, 9, 2), java.time.LocalTime.of(2, 0), zoneId).toOffsetDateTime();

        Booking crossBooking = Booking.builder()
                .id(1L)
                .room(roomAlpha)
                .startTime(crossStart)
                .endTime(crossEnd)
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findEffectiveBookingsForRoomInInterval(eq(10L), any(), any()))
                .thenReturn(List.of(crossBooking));

        UtilizationReportResponse report = analyticsService.getUtilization(startDate, endDate, null, 10L);

        RoomUtilizationResponse roomReport = report.getRooms().get(0);
        assertThat(roomReport.getTotalBookedMinutes()).isEqualTo(180L);
    }

    @Test
    @DisplayName("4 & 5. Multiple rooms across different timezones calculate overall utilization")
    void testMultipleRoomsDifferentTimezones() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        when(roomRepository.findAll()).thenReturn(List.of(roomAlpha, roomBeta));
        when(timezoneService.getLocationZoneId(locationMumbai)).thenReturn(ZoneId.of("Asia/Kolkata"));
        when(timezoneService.getLocationZoneId(locationLondon)).thenReturn(ZoneId.of("Europe/London"));

        // Room Alpha (1440 available, 720 booked -> 50%)
        Booking bAlpha = Booking.builder()
                .id(1L)
                .room(roomAlpha)
                .startTime(OffsetDateTime.parse("2026-09-01T04:30:00Z"))
                .endTime(OffsetDateTime.parse("2026-09-01T16:30:00Z"))
                .status(BookingStatus.CONFIRMED)
                .build();
        when(bookingRepository.findEffectiveBookingsForRoomInInterval(eq(10L), any(), any()))
                .thenReturn(List.of(bAlpha));

        // Room Beta (1440 available, 0 booked -> 0%)
        when(bookingRepository.findEffectiveBookingsForRoomInInterval(eq(20L), any(), any()))
                .thenReturn(List.of());

        UtilizationReportResponse report = analyticsService.getUtilization(date, date, null, null);

        assertThat(report.getRooms()).hasSize(2);
        // Total available: 2880, Total booked: 720 -> (720 / 2880) * 100 = 25.0%
        assertThat(report.getOverallUtilizationPercentage()).isEqualTo(25.0);
    }
}
