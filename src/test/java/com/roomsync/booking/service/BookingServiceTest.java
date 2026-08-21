package com.roomsync.booking.service;

import com.roomsync.booking.dto.BookingResponse;
import com.roomsync.booking.dto.CreateBookingRequest;
import com.roomsync.booking.dto.RescheduleBookingRequest;
import com.roomsync.booking.entity.Booking;
import com.roomsync.booking.entity.BookingStatus;
import com.roomsync.booking.exception.BookingAlreadyCancelledException;
import com.roomsync.booking.exception.BookingAlreadyCompletedException;
import com.roomsync.booking.exception.BookingNotFoundException;
import com.roomsync.booking.exception.BookingOverlapException;
import com.roomsync.booking.exception.InvalidBookingTimeException;
import com.roomsync.booking.exception.RoomNotBookableException;
import com.roomsync.booking.exception.UnauthorizedBookingOperationException;
import com.roomsync.booking.repository.BookingRepository;
import com.roomsync.common.response.PageResponse;
import com.roomsync.location.entity.Location;
import com.roomsync.location.exception.UnauthorizedLocationAccessException;
import com.roomsync.room.entity.Room;
import com.roomsync.room.entity.RoomStatus;
import com.roomsync.room.exception.RoomNotFoundException;
import com.roomsync.room.repository.RoomRepository;
import com.roomsync.user.entity.User;
import com.roomsync.user.entity.UserRole;
import com.roomsync.user.exception.UserNotFoundException;
import com.roomsync.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private UserRepository userRepository;

    // Fixed clock at 2026-08-20T09:00:00Z
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-20T09:00:00Z"), ZoneOffset.UTC);

    private BookingService bookingService;

    private Location locationMumbai;
    private Location locationPune;
    private User user1;
    private User user2;
    private Room activeAvailableRoom;
    private Room lockedRoom;
    private Room inactiveRoom;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(bookingRepository, roomRepository, userRepository, clock);

        locationMumbai = Location.builder().id(1L).name("Mumbai").code("MUM").active(true).build();
        locationPune = Location.builder().id(2L).name("Pune").code("PUN").active(true).build();

        user1 = User.builder().id(1L).name("User One").email("user1@example.com").password("pass").role(UserRole.USER).location(locationMumbai).build();
        user2 = User.builder().id(2L).name("User Two").email("user2@example.com").password("pass").role(UserRole.USER).location(locationPune).build();

        activeAvailableRoom = Room.builder()
                .id(10L)
                .location(locationMumbai)
                .name("Taj Mahal")
                .capacity(10)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build();

        lockedRoom = Room.builder()
                .id(20L)
                .location(locationMumbai)
                .name("Qutub Minar")
                .capacity(8)
                .status(RoomStatus.LOCKED)
                .active(true)
                .build();

        inactiveRoom = Room.builder()
                .id(30L)
                .location(locationMumbai)
                .name("Red Fort")
                .capacity(15)
                .status(RoomStatus.AVAILABLE)
                .active(false)
                .build();
    }

    @Nested
    @DisplayName("createBooking tests")
    class CreateBookingTests {

        @Test
        @DisplayName("Should successfully create booking when all rules pass")
        void shouldCreateBookingSuccessfully() {
            OffsetDateTime startTime = OffsetDateTime.parse("2026-08-20T10:00:00Z");
            OffsetDateTime endTime = OffsetDateTime.parse("2026-08-20T11:00:00Z");

            CreateBookingRequest request = CreateBookingRequest.builder()
                    .roomId(10L)
                    .startTime(startTime)
                    .endTime(endTime)
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
            when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeAvailableRoom));
            when(bookingRepository.existsOverlappingBooking(10L, startTime, endTime)).thenReturn(false);
            when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> {
                Booking b = i.getArgument(0);
                b.setId(100L);
                return b;
            });

            BookingResponse response = bookingService.createBooking(1L, request);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(100L);
            assertThat(response.getRoomId()).isEqualTo(10L);
            assertThat(response.getUserId()).isEqualTo(1L);
            assertThat(response.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        }

        @Test
        @DisplayName("Should throw UnauthorizedLocationAccessException when user books room in another location")
        void shouldThrowWhenBookingRoomInOtherLocation() {
            OffsetDateTime startTime = OffsetDateTime.parse("2026-08-20T10:00:00Z");
            OffsetDateTime endTime = OffsetDateTime.parse("2026-08-20T11:00:00Z");

            CreateBookingRequest request = CreateBookingRequest.builder()
                    .roomId(10L)
                    .startTime(startTime)
                    .endTime(endTime)
                    .build();

            when(userRepository.findById(2L)).thenReturn(Optional.of(user2));
            when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeAvailableRoom));

            assertThatThrownBy(() -> bookingService.createBooking(2L, request))
                    .isInstanceOf(UnauthorizedLocationAccessException.class);
        }

        @Test
        @DisplayName("Should throw UserNotFoundException when user does not exist")
        void shouldThrowWhenUserNotFound() {
            CreateBookingRequest request = CreateBookingRequest.builder()
                    .roomId(10L)
                    .startTime(OffsetDateTime.parse("2026-08-20T10:00:00Z"))
                    .endTime(OffsetDateTime.parse("2026-08-20T11:00:00Z"))
                    .build();

            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookingService.createBooking(99L, request))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw RoomNotFoundException when room does not exist")
        void shouldThrowWhenRoomNotFound() {
            CreateBookingRequest request = CreateBookingRequest.builder()
                    .roomId(99L)
                    .startTime(OffsetDateTime.parse("2026-08-20T10:00:00Z"))
                    .endTime(OffsetDateTime.parse("2026-08-20T11:00:00Z"))
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
            when(roomRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookingService.createBooking(1L, request))
                    .isInstanceOf(RoomNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw RoomNotBookableException when room is inactive")
        void shouldThrowWhenRoomInactive() {
            CreateBookingRequest request = CreateBookingRequest.builder()
                    .roomId(30L)
                    .startTime(OffsetDateTime.parse("2026-08-20T10:00:00Z"))
                    .endTime(OffsetDateTime.parse("2026-08-20T11:00:00Z"))
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
            when(roomRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(inactiveRoom));

            assertThatThrownBy(() -> bookingService.createBooking(1L, request))
                    .isInstanceOf(RoomNotBookableException.class)
                    .hasMessageContaining("inactive");
        }

        @Test
        @DisplayName("Should throw RoomNotBookableException when room is locked")
        void shouldThrowWhenRoomLocked() {
            CreateBookingRequest request = CreateBookingRequest.builder()
                    .roomId(20L)
                    .startTime(OffsetDateTime.parse("2026-08-20T10:00:00Z"))
                    .endTime(OffsetDateTime.parse("2026-08-20T11:00:00Z"))
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
            when(roomRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(lockedRoom));

            assertThatThrownBy(() -> bookingService.createBooking(1L, request))
                    .isInstanceOf(RoomNotBookableException.class)
                    .hasMessageContaining("locked");
        }

        @Test
        @DisplayName("Should throw InvalidBookingTimeException when start >= end")
        void shouldThrowWhenStartAfterEnd() {
            CreateBookingRequest request = CreateBookingRequest.builder()
                    .roomId(10L)
                    .startTime(OffsetDateTime.parse("2026-08-20T11:00:00Z"))
                    .endTime(OffsetDateTime.parse("2026-08-20T10:00:00Z"))
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
            when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeAvailableRoom));

            assertThatThrownBy(() -> bookingService.createBooking(1L, request))
                    .isInstanceOf(InvalidBookingTimeException.class)
                    .hasMessageContaining("strictly before");
        }

        @Test
        @DisplayName("Should throw InvalidBookingTimeException when duration is less than 15 minutes")
        void shouldThrowWhenDurationLessThan15Min() {
            CreateBookingRequest request = CreateBookingRequest.builder()
                    .roomId(10L)
                    .startTime(OffsetDateTime.parse("2026-08-20T10:00:00Z"))
                    .endTime(OffsetDateTime.parse("2026-08-20T10:10:00Z"))
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
            when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeAvailableRoom));

            assertThatThrownBy(() -> bookingService.createBooking(1L, request))
                    .isInstanceOf(InvalidBookingTimeException.class);
        }

        @Test
        @DisplayName("Should throw InvalidBookingTimeException when start time is not on 15-minute boundary")
        void shouldThrowWhenNotOn15MinBoundary() {
            CreateBookingRequest request = CreateBookingRequest.builder()
                    .roomId(10L)
                    .startTime(OffsetDateTime.parse("2026-08-20T10:05:00Z"))
                    .endTime(OffsetDateTime.parse("2026-08-20T10:30:00Z"))
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
            when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeAvailableRoom));

            assertThatThrownBy(() -> bookingService.createBooking(1L, request))
                    .isInstanceOf(InvalidBookingTimeException.class)
                    .hasMessageContaining("15-minute boundary");
        }

        @Test
        @DisplayName("Should throw InvalidBookingTimeException when seconds or nanos are non-zero")
        void shouldThrowWhenSecondsNonZero() {
            CreateBookingRequest request = CreateBookingRequest.builder()
                    .roomId(10L)
                    .startTime(OffsetDateTime.parse("2026-08-20T10:15:30Z"))
                    .endTime(OffsetDateTime.parse("2026-08-20T10:45:00Z"))
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
            when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeAvailableRoom));

            assertThatThrownBy(() -> bookingService.createBooking(1L, request))
                    .isInstanceOf(InvalidBookingTimeException.class)
                    .hasMessageContaining("zero seconds");
        }

        @Test
        @DisplayName("Should throw InvalidBookingTimeException when start time is in past relative to clock")
        void shouldThrowWhenStartTimeInPast() {
            CreateBookingRequest request = CreateBookingRequest.builder()
                    .roomId(10L)
                    .startTime(OffsetDateTime.parse("2026-08-20T08:00:00Z"))
                    .endTime(OffsetDateTime.parse("2026-08-20T08:30:00Z"))
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
            when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeAvailableRoom));

            assertThatThrownBy(() -> bookingService.createBooking(1L, request))
                    .isInstanceOf(InvalidBookingTimeException.class)
                    .hasMessageContaining("past");
        }

        @Test
        @DisplayName("Should throw BookingOverlapException when confirmed booking overlaps")
        void shouldThrowWhenOverlapDetected() {
            OffsetDateTime startTime = OffsetDateTime.parse("2026-08-20T10:00:00Z");
            OffsetDateTime endTime = OffsetDateTime.parse("2026-08-20T11:00:00Z");

            CreateBookingRequest request = CreateBookingRequest.builder()
                    .roomId(10L)
                    .startTime(startTime)
                    .endTime(endTime)
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
            when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeAvailableRoom));
            when(bookingRepository.existsOverlappingBooking(10L, startTime, endTime)).thenReturn(true);

            assertThatThrownBy(() -> bookingService.createBooking(1L, request))
                    .isInstanceOf(BookingOverlapException.class)
                    .hasMessageContaining("overlaps");

            verify(bookingRepository, never()).save(any(Booking.class));
        }
    }

    @Nested
    @DisplayName("getBooking tests")
    class GetBookingTests {

        @Test
        @DisplayName("Should return booking for owner")
        void shouldReturnBookingForOwner() {
            Booking booking = Booking.builder()
                    .id(100L)
                    .user(user1)
                    .room(activeAvailableRoom)
                    .startTime(OffsetDateTime.parse("2026-08-20T10:00:00Z"))
                    .endTime(OffsetDateTime.parse("2026-08-20T11:00:00Z"))
                    .status(BookingStatus.CONFIRMED)
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
            when(bookingRepository.findById(100L)).thenReturn(Optional.of(booking));

            BookingResponse response = bookingService.getBooking(100L, 1L);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(100L);
            assertThat(response.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        }

        @Test
        @DisplayName("Should return effective COMPLETED status when confirmed and endTime in past")
        void shouldReturnEffectiveCompletedStatus() {
            Booking pastBooking = Booking.builder()
                    .id(100L)
                    .user(user1)
                    .room(activeAvailableRoom)
                    .startTime(OffsetDateTime.parse("2026-08-20T07:00:00Z"))
                    .endTime(OffsetDateTime.parse("2026-08-20T08:00:00Z"))
                    .status(BookingStatus.CONFIRMED)
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
            when(bookingRepository.findById(100L)).thenReturn(Optional.of(pastBooking));

            BookingResponse response = bookingService.getBooking(100L, 1L);

            assertThat(response.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        }

        @Test
        @DisplayName("Should throw UnauthorizedBookingOperationException when accessing another user's booking")
        void shouldThrowWhenNotOwner() {
            Booking booking = Booking.builder()
                    .id(100L)
                    .user(user1)
                    .room(activeAvailableRoom)
                    .startTime(OffsetDateTime.parse("2026-08-20T10:00:00Z"))
                    .endTime(OffsetDateTime.parse("2026-08-20T11:00:00Z"))
                    .status(BookingStatus.CONFIRMED)
                    .build();

            when(userRepository.findById(2L)).thenReturn(Optional.of(user2));
            when(bookingRepository.findById(100L)).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> bookingService.getBooking(100L, 2L))
                    .isInstanceOf(UnauthorizedBookingOperationException.class);
        }
    }

    @Nested
    @DisplayName("getMyBookings tests")
    class GetMyBookingsTests {

        @Test
        @DisplayName("Should return paginated bookings for requesting user")
        void shouldReturnMyBookings() {
            Booking booking = Booking.builder()
                    .id(100L)
                    .user(user1)
                    .room(activeAvailableRoom)
                    .startTime(OffsetDateTime.parse("2026-08-20T10:00:00Z"))
                    .endTime(OffsetDateTime.parse("2026-08-20T11:00:00Z"))
                    .status(BookingStatus.CONFIRMED)
                    .build();

            Page<Booking> page = new PageImpl<>(List.of(booking), PageRequest.of(0, 10), 1);

            when(userRepository.existsById(1L)).thenReturn(true);
            when(bookingRepository.findAllByUserId(eq(1L), any(Pageable.class))).thenReturn(page);

            PageResponse<BookingResponse> response = bookingService.getMyBookings(1L, PageRequest.of(0, 10));

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getId()).isEqualTo(100L);
        }
    }

    @Nested
    @DisplayName("rescheduleBooking tests")
    class RescheduleBookingTests {

        @Test
        @DisplayName("Should successfully reschedule booking when valid and no overlap with others")
        void shouldRescheduleSuccessfully() {
            Booking booking = Booking.builder()
                    .id(100L)
                    .user(user1)
                    .room(activeAvailableRoom)
                    .startTime(OffsetDateTime.parse("2026-08-20T10:00:00Z"))
                    .endTime(OffsetDateTime.parse("2026-08-20T11:00:00Z"))
                    .status(BookingStatus.CONFIRMED)
                    .build();

            OffsetDateTime newStart = OffsetDateTime.parse("2026-08-20T14:00:00Z");
            OffsetDateTime newEnd = OffsetDateTime.parse("2026-08-20T15:00:00Z");
            RescheduleBookingRequest request = RescheduleBookingRequest.builder()
                    .startTime(newStart)
                    .endTime(newEnd)
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
            when(bookingRepository.findById(100L)).thenReturn(Optional.of(booking));
            when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeAvailableRoom));
            when(bookingRepository.existsOverlappingBookingExcludingBooking(10L, 100L, newStart, newEnd)).thenReturn(false);
            when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

            BookingResponse response = bookingService.rescheduleBooking(100L, 1L, request);

            assertThat(response.getStartTime()).isEqualTo(newStart);
            assertThat(response.getEndTime()).isEqualTo(newEnd);
        }
    }

    @Nested
    @DisplayName("cancelBooking tests")
    class CancelBookingTests {

        @Test
        @DisplayName("Should soft-cancel confirmed booking")
        void shouldCancelConfirmedBooking() {
            Booking booking = Booking.builder()
                    .id(100L)
                    .user(user1)
                    .room(activeAvailableRoom)
                    .startTime(OffsetDateTime.parse("2026-08-20T10:00:00Z"))
                    .endTime(OffsetDateTime.parse("2026-08-20T11:00:00Z"))
                    .status(BookingStatus.CONFIRMED)
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
            when(bookingRepository.findById(100L)).thenReturn(Optional.of(booking));
            when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeAvailableRoom));

            bookingService.cancelBooking(100L, 1L);

            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            verify(bookingRepository).save(booking);
        }
    }
}
