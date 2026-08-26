package com.roomsync.booking.service;

import com.roomsync.admin.dto.AdminRequestResponse;
import com.roomsync.admin.entity.AdminRequest;
import com.roomsync.admin.repository.AdminRequestRepository;
import com.roomsync.booking.dto.BookingResponse;
import com.roomsync.booking.dto.ContactAdminOccurrenceRequest;
import com.roomsync.booking.dto.CreateRecurringBookingRequest;
import com.roomsync.booking.dto.RecurringConfirmationResponse;
import com.roomsync.booking.dto.RecurringOccurrencePreview;
import com.roomsync.booking.dto.RecurringOccurrenceResult;
import com.roomsync.booking.dto.RecurringPreviewResponse;
import com.roomsync.booking.entity.Booking;
import com.roomsync.booking.entity.BookingSeries;
import com.roomsync.booking.entity.BookingSeriesStatus;
import com.roomsync.booking.entity.BookingStatus;
import com.roomsync.booking.entity.RecurrenceFrequency;
import com.roomsync.booking.exception.BookingOverlapException;
import com.roomsync.booking.repository.BookingRepository;
import com.roomsync.booking.repository.BookingSeriesRepository;
import com.roomsync.common.time.BookingInterval;
import com.roomsync.common.time.TimeService;
import com.roomsync.common.time.TimezoneService;
import com.roomsync.location.entity.Location;
import com.roomsync.location.repository.LocationRepository;
import com.roomsync.room.entity.Room;
import com.roomsync.room.entity.RoomStatus;
import com.roomsync.room.repository.RoomRepository;
import com.roomsync.user.entity.Role;
import com.roomsync.user.entity.User;
import com.roomsync.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecurringBookingServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private BookingSeriesRepository bookingSeriesRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private com.roomsync.booking.repository.BookingOccurrenceExceptionRepository bookingOccurrenceExceptionRepository;

    @Mock
    private com.roomsync.audit.service.AuditService auditService;

    @Mock
    private AdminRequestRepository adminRequestRepository;

    @Mock
    private RecurrenceGenerator recurrenceGenerator;

    @Mock
    private RecurringConflictService recurringConflictService;

    @Mock
    private RecurringOccurrenceWorker recurringOccurrenceWorker;

    @Mock
    private BookingConcurrencyService bookingConcurrencyService;

    @Mock
    private BookingService bookingService;

    @Mock
    private TimezoneService timezoneService;

    @Mock
    private TimeService timeService;

    @Spy
    private Clock clock = Clock.systemUTC();

    @InjectMocks
    private RecurringBookingService recurringBookingService;

    private User user;
    private Location location;
    private Room room;
    private Room alternateRoom;

    @BeforeEach
    void setUp() {
        location = Location.builder()
                .id(1L)
                .name("Mumbai HQ")
                .timezone("Asia/Kolkata")
                .active(true)
                .build();

        Role userRole = Role.builder().id(1L).name("USER").build();

        user = User.builder()
                .id(100L)
                .name("Alice")
                .email("alice@roomsync.com")
                .role(userRole)
                .location(location)
                .active(true)
                .build();

        room = Room.builder()
                .id(10L)
                .name("Room Alpha")
                .location(location)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build();

        alternateRoom = Room.builder()
                .id(20L)
                .name("Room Beta")
                .location(location)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("previewSeries: Generates preview without persisting bookings")
    void testPreviewSeries() {
        CreateRecurringBookingRequest request = CreateRecurringBookingRequest.builder()
                .roomId(10L)
                .seriesName("Daily Standup")
                .frequency(RecurrenceFrequency.DAILY)
                .startDate(LocalDate.of(2026, 9, 1))
                .occurrenceCount(3)
                .startLocalTime(LocalTime.of(10, 0))
                .endLocalTime(LocalTime.of(10, 30))
                .reason("Standup")
                .build();

        when(userRepository.findById(100L)).thenReturn(Optional.of(user));
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room));

        List<RecurrenceGenerator.OccurrenceDate> dates = List.of(
                new RecurrenceGenerator.OccurrenceDate(1, LocalDate.of(2026, 9, 1)),
                new RecurrenceGenerator.OccurrenceDate(2, LocalDate.of(2026, 9, 2)),
                new RecurrenceGenerator.OccurrenceDate(3, LocalDate.of(2026, 9, 3))
        );
        when(recurrenceGenerator.generateOccurrences(any(), any(), any(), any(), any(), any())).thenReturn(dates);

        List<RecurringOccurrencePreview> previews = List.of(
                RecurringOccurrencePreview.builder().occurrenceIndex(1).availability("AVAILABLE").build(),
                RecurringOccurrencePreview.builder().occurrenceIndex(2).availability("CONFLICT").conflictReason("Overlap").build(),
                RecurringOccurrencePreview.builder().occurrenceIndex(3).availability("AVAILABLE").build()
        );
        when(recurringConflictService.evaluateOccurrences(eq(10L), any(), any(), eq(dates))).thenReturn(previews);

        RecurringPreviewResponse response = recurringBookingService.previewSeries(100L, request);

        assertThat(response.getTotalOccurrences()).isEqualTo(3);
        assertThat(response.getAvailableCount()).isEqualTo(2);
        assertThat(response.getConflictCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("createAndConfirmSeries: Sets PARTIALLY_CONFIRMED on partial conflict")
    void testCreateAndConfirmSeriesPartialSuccess() {
        CreateRecurringBookingRequest request = CreateRecurringBookingRequest.builder()
                .roomId(10L)
                .seriesName("Daily Standup")
                .frequency(RecurrenceFrequency.DAILY)
                .startDate(LocalDate.of(2026, 9, 1))
                .occurrenceCount(3)
                .startLocalTime(LocalTime.of(10, 0))
                .endLocalTime(LocalTime.of(10, 30))
                .reason("Standup")
                .build();

        when(userRepository.findById(100L)).thenReturn(Optional.of(user));
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room));

        List<RecurrenceGenerator.OccurrenceDate> dates = List.of(
                new RecurrenceGenerator.OccurrenceDate(1, LocalDate.of(2026, 9, 1)),
                new RecurrenceGenerator.OccurrenceDate(2, LocalDate.of(2026, 9, 2)),
                new RecurrenceGenerator.OccurrenceDate(3, LocalDate.of(2026, 9, 3))
        );
        when(recurrenceGenerator.generateOccurrences(any(), any(), any(), any(), any(), any())).thenReturn(dates);

        BookingSeries savedSeries = BookingSeries.builder()
                .id(1L)
                .user(user)
                .seriesName("Daily Standup")
                .frequency(RecurrenceFrequency.DAILY)
                .startDate(LocalDate.of(2026, 9, 1))
                .occurrenceCount(3)
                .startLocalTime(LocalTime.of(10, 0))
                .endLocalTime(LocalTime.of(10, 30))
                .timezone("Asia/Kolkata")
                .status(BookingSeriesStatus.ACTIVE)
                .build();
        when(recurringOccurrenceWorker.createInitialSeries(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(savedSeries);

        // Occurrence 1 succeeds
        when(recurringOccurrenceWorker.processOccurrence(eq(1L), eq(1), any(), eq(10L), any()))
                .thenReturn(RecurringOccurrenceResult.builder().occurrenceIndex(1).status("CONFIRMED").bookingId(101L).build());

        // Occurrence 2 conflicts
        when(recurringOccurrenceWorker.processOccurrence(eq(1L), eq(2), any(), eq(10L), any()))
                .thenThrow(new BookingOverlapException("Slot occupied"));

        // Occurrence 3 succeeds
        when(recurringOccurrenceWorker.processOccurrence(eq(1L), eq(3), any(), eq(10L), any()))
                .thenReturn(RecurringOccurrenceResult.builder().occurrenceIndex(3).status("CONFIRMED").bookingId(103L).build());

        doNothing().when(recurringOccurrenceWorker).updateSeriesStatus(1L, BookingSeriesStatus.PARTIALLY_CONFIRMED);

        RecurringConfirmationResponse response = recurringBookingService.createAndConfirmSeries(100L, request);

        assertThat(response.getSeriesStatus()).isEqualTo(BookingSeriesStatus.PARTIALLY_CONFIRMED);
        assertThat(response.getConfirmedCount()).isEqualTo(2);
        assertThat(response.getConflictCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("cancelOccurrence: Cancels single occurrence booking")
    void testCancelOccurrence() {
        BookingSeries series = BookingSeries.builder().id(1L).user(user).build();
        when(userRepository.findById(100L)).thenReturn(Optional.of(user));
        when(bookingSeriesRepository.findById(1L)).thenReturn(Optional.of(series));

        Booking booking = Booking.builder().id(55L).user(user).series(series).occurrenceIndex(2).build();
        when(bookingRepository.findBySeriesIdAndOccurrenceIndex(1L, 2)).thenReturn(Optional.of(booking));

        doNothing().when(bookingService).cancelBooking(55L, 100L, "Individual cancel");

        recurringBookingService.cancelOccurrence(1L, 2, 100L, "Individual cancel");

        verify(bookingService).cancelBooking(55L, 100L, "Individual cancel");
    }

    @Test
    @DisplayName("contactAdminForOccurrence: Creates AdminRequest for conflict")
    void testContactAdminForOccurrence() {
        BookingSeries series = BookingSeries.builder().id(1L).user(user).seriesName("Team Sync").build();
        when(userRepository.findById(100L)).thenReturn(Optional.of(user));
        when(bookingSeriesRepository.findById(1L)).thenReturn(Optional.of(series));
        when(bookingRepository.findBySeriesIdAndOccurrenceIndex(1L, 2)).thenReturn(Optional.empty());

        AdminRequest savedReq = AdminRequest.builder()
                .id(999L)
                .requesterUser(user)
                .bookingSeries(series)
                .requestType("RECURRING_CONFLICT")
                .message("Need help with room")
                .build();
        when(adminRequestRepository.save(any(AdminRequest.class))).thenReturn(savedReq);

        ContactAdminOccurrenceRequest req = ContactAdminOccurrenceRequest.builder()
                .message("Need help with room")
                .build();

        AdminRequestResponse response = recurringBookingService.contactAdminForOccurrence(1L, 2, 100L, req);

        assertThat(response.getId()).isEqualTo(999L);
        assertThat(response.getRequestType()).isEqualTo("RECURRING_CONFLICT");
    }
}
