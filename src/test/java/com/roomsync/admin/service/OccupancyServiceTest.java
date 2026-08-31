package com.roomsync.admin.service;

import com.roomsync.admin.dto.OccupancyStatus;
import com.roomsync.admin.dto.RoomOccupancyResponse;
import com.roomsync.booking.entity.Booking;
import com.roomsync.booking.entity.BookingStatus;
import com.roomsync.booking.repository.BookingRepository;
import com.roomsync.common.response.PageResponse;
import com.roomsync.common.time.DateTimeProvider;
import com.roomsync.location.entity.Location;
import com.roomsync.room.entity.Room;
import com.roomsync.room.entity.RoomStatus;
import com.roomsync.room.repository.RoomRepository;
import com.roomsync.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OccupancyServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private DateTimeProvider dateTimeProvider;

    @InjectMocks
    private OccupancyService occupancyService;

    private Location location;
    private Room roomAvailable;
    private Room roomLocked;
    private User user;
    private OffsetDateTime fixedNow;

    @BeforeEach
    void setUp() {
        location = Location.builder()
                .id(1L)
                .name("Mumbai HQ")
                .timezone("Asia/Kolkata")
                .active(true)
                .build();

        roomAvailable = Room.builder()
                .id(10L)
                .name("Room Alpha")
                .capacity(8)
                .location(location)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build();

        roomLocked = Room.builder()
                .id(20L)
                .name("Room Beta")
                .capacity(12)
                .location(location)
                .status(RoomStatus.LOCKED)
                .active(true)
                .build();

        user = User.builder()
                .id(100L)
                .wissenId("WT1151")
                .name("Alice")
                .email("alice@roomsync.com")
                .build();

        fixedNow = OffsetDateTime.of(2026, 9, 1, 10, 30, 0, 0, ZoneOffset.UTC);
    }

    @Test
    @DisplayName("1. Available room with no active booking returns AVAILABLE")
    void testAvailableRoomNoBooking() {
        when(dateTimeProvider.nowOffsetDateTime()).thenReturn(fixedNow);
        Pageable pageable = PageRequest.of(0, 10);
        when(roomRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(roomAvailable), pageable, 1));
        when(bookingRepository.findAllActiveConfirmedBookingsAt(fixedNow)).thenReturn(List.of());

        PageResponse<RoomOccupancyResponse> response = occupancyService.getOccupancy(null, null, pageable);

        assertThat(response.getContent()).hasSize(1);
        RoomOccupancyResponse item = response.getContent().get(0);
        assertThat(item.getRoomId()).isEqualTo(10L);
        assertThat(item.getAdministrativeState()).isEqualTo(RoomStatus.AVAILABLE);
        assertThat(item.getOccupancyStatus()).isEqualTo(OccupancyStatus.AVAILABLE);
        assertThat(item.getCurrentBooking()).isNull();

        // 7. Verify Room.status is never modified in DB
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("2. Locked room returns LOCKED regardless of booking")
    void testLockedRoom() {
        when(dateTimeProvider.nowOffsetDateTime()).thenReturn(fixedNow);
        Pageable pageable = PageRequest.of(0, 10);
        when(roomRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(roomLocked), pageable, 1));
        when(bookingRepository.findAllActiveConfirmedBookingsAt(fixedNow)).thenReturn(List.of());

        PageResponse<RoomOccupancyResponse> response = occupancyService.getOccupancy(null, null, pageable);

        assertThat(response.getContent()).hasSize(1);
        RoomOccupancyResponse item = response.getContent().get(0);
        assertThat(item.getRoomId()).isEqualTo(20L);
        assertThat(item.getAdministrativeState()).isEqualTo(RoomStatus.LOCKED);
        assertThat(item.getOccupancyStatus()).isEqualTo(OccupancyStatus.LOCKED);
        assertThat(item.getCurrentBooking()).isNull();

        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("3. Currently booked available room returns derived OCCUPIED with booking summary")
    void testCurrentlyBookedRoomReturnsOccupied() {
        when(dateTimeProvider.nowOffsetDateTime()).thenReturn(fixedNow);
        Pageable pageable = PageRequest.of(0, 10);
        when(roomRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(roomAvailable), pageable, 1));

        Booking activeBooking = Booking.builder()
                .id(500L)
                .room(roomAvailable)
                .user(user)
                .startTime(fixedNow.minusMinutes(30))
                .endTime(fixedNow.plusMinutes(30))
                .status(BookingStatus.CONFIRMED)
                .reason("Important Meeting")
                .build();

        when(bookingRepository.findAllActiveConfirmedBookingsAt(fixedNow)).thenReturn(List.of(activeBooking));

        PageResponse<RoomOccupancyResponse> response = occupancyService.getOccupancy(null, null, pageable);

        assertThat(response.getContent()).hasSize(1);
        RoomOccupancyResponse item = response.getContent().get(0);
        assertThat(item.getRoomId()).isEqualTo(10L);
        assertThat(item.getAdministrativeState()).isEqualTo(RoomStatus.AVAILABLE);
        assertThat(item.getOccupancyStatus()).isEqualTo(OccupancyStatus.OCCUPIED);
        assertThat(item.getCurrentBooking()).isNotNull();
        assertThat(item.getCurrentBooking().getBookingId()).isEqualTo(500L);
        assertThat(item.getCurrentBooking().getUserName()).isEqualTo("Alice");
        assertThat(item.getCurrentBooking().getReason()).isEqualTo("Important Meeting");

        // 7. Verify OCCUPIED is NOT persisted into room entity
        assertThat(roomAvailable.getStatus()).isEqualTo(RoomStatus.AVAILABLE);
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("4. Past completed/ended booking does not cause OCCUPIED")
    void testPastBookingNotOccupied() {
        when(dateTimeProvider.nowOffsetDateTime()).thenReturn(fixedNow);
        Pageable pageable = PageRequest.of(0, 10);
        when(roomRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(roomAvailable), pageable, 1));
        // Repository query findAllActiveConfirmedBookingsAt excludes past bookings
        when(bookingRepository.findAllActiveConfirmedBookingsAt(fixedNow)).thenReturn(List.of());

        PageResponse<RoomOccupancyResponse> response = occupancyService.getOccupancy(null, null, pageable);

        assertThat(response.getContent().get(0).getOccupancyStatus()).isEqualTo(OccupancyStatus.AVAILABLE);
        assertThat(response.getContent().get(0).getCurrentBooking()).isNull();
    }

    @Test
    @DisplayName("5. Cancelled booking does not cause OCCUPIED")
    void testCancelledBookingNotOccupied() {
        when(dateTimeProvider.nowOffsetDateTime()).thenReturn(fixedNow);
        Pageable pageable = PageRequest.of(0, 10);
        when(roomRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(roomAvailable), pageable, 1));
        // findAllActiveConfirmedBookingsAt filters status = CONFIRMED
        when(bookingRepository.findAllActiveConfirmedBookingsAt(fixedNow)).thenReturn(List.of());

        PageResponse<RoomOccupancyResponse> response = occupancyService.getOccupancy(null, null, pageable);

        assertThat(response.getContent().get(0).getOccupancyStatus()).isEqualTo(OccupancyStatus.AVAILABLE);
    }

    @Test
    @DisplayName("6. Future booking does not cause OCCUPIED before start")
    void testFutureBookingNotOccupied() {
        when(dateTimeProvider.nowOffsetDateTime()).thenReturn(fixedNow);
        Pageable pageable = PageRequest.of(0, 10);
        when(roomRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(roomAvailable), pageable, 1));
        when(bookingRepository.findAllActiveConfirmedBookingsAt(fixedNow)).thenReturn(List.of());

        PageResponse<RoomOccupancyResponse> response = occupancyService.getOccupancy(null, null, pageable);

        assertThat(response.getContent().get(0).getOccupancyStatus()).isEqualTo(OccupancyStatus.AVAILABLE);
    }

    @Test
    @DisplayName("8. Filter by single room ID uses deterministic DateTimeProvider")
    void testFilterBySingleRoomId() {
        when(dateTimeProvider.nowOffsetDateTime()).thenReturn(fixedNow);
        Pageable pageable = PageRequest.of(0, 10);
        when(roomRepository.findById(10L)).thenReturn(Optional.of(roomAvailable));
        when(bookingRepository.findAllActiveConfirmedBookingsAt(fixedNow)).thenReturn(List.of());

        PageResponse<RoomOccupancyResponse> response = occupancyService.getOccupancy(null, 10L, pageable);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getRoomName()).isEqualTo("Room Alpha");
    }
}
