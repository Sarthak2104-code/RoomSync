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
import com.roomsync.location.exception.LocationNotActiveException;
import com.roomsync.location.exception.UnauthorizedLocationAccessException;
import com.roomsync.room.entity.Room;
import com.roomsync.room.entity.RoomStatus;
import com.roomsync.room.exception.RoomNotFoundException;
import com.roomsync.room.repository.RoomRepository;
import com.roomsync.user.entity.User;
import com.roomsync.user.entity.UserRole;
import com.roomsync.user.exception.UserNotFoundException;
import com.roomsync.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Service managing Booking lifecycle, business rules, location boundaries, and concurrency protection:
 * - Multi-location access control (users restricted to their location; admins global)
 * - Application-level Room PESSIMISTIC_WRITE row locking for serialization
 * - Database-level PostgreSQL EXCLUDE USING GIST exclusion constraint safety net
 * - 15-minute minimum duration and boundary alignment
 * - Start time < End time, future start times
 * - Active & Available room and location checks
 * - Interval overlap detection
 * - Ownership checks and effective COMPLETED status calculation
 * - Soft cancellation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "startTime", "endTime", "status", "createdAt", "updatedAt"
    );

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * Creates a new booking within a transactional boundary synchronized via a PESSIMISTIC_WRITE lock on the Room.
     */
    @Transactional
    public BookingResponse createBooking(Long userId, CreateBookingRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Step 5: Acquire PESSIMISTIC_WRITE lock on Room to serialize concurrent booking attempts on the same room
        Room room = roomRepository.findByIdForUpdate(request.getRoomId())
                .orElseThrow(() -> new RoomNotFoundException(request.getRoomId()));

        if (!room.getLocation().isActive()) {
            throw new LocationNotActiveException(
                    String.format("Location '%s' is inactive and room cannot be booked", room.getLocation().getName())
            );
        }

        if (user.getRole() == UserRole.USER && !room.getLocation().getId().equals(user.getLocation().getId())) {
            throw new UnauthorizedLocationAccessException("User cannot book a room in another location");
        }

        if (!room.isActive()) {
            throw new RoomNotBookableException(
                    String.format("Room '%s' is inactive and cannot be booked", room.getName())
            );
        }

        if (room.getStatus() == RoomStatus.LOCKED) {
            throw new RoomNotBookableException(
                    String.format("Room '%s' is currently locked and cannot be booked", room.getName())
            );
        }

        validateBookingTimes(request.getStartTime(), request.getEndTime());

        if (bookingRepository.existsOverlappingBooking(room.getId(), request.getStartTime(), request.getEndTime())) {
            throw new BookingOverlapException("The requested time slot overlaps with an existing confirmed booking for this room");
        }

        Booking booking = Booking.builder()
                .room(room)
                .user(user)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(BookingStatus.CONFIRMED)
                .build();

        Booking savedBooking = bookingRepository.save(booking);
        log.info("Created booking id: {} for user: {} in room: {} (location: {})",
                savedBooking.getId(), userId, room.getId(), room.getLocation().getCode());
        return BookingResponse.fromEntity(savedBooking, clock);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(Long bookingId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (user.getRole() == UserRole.USER) {
            if (!booking.getUser().getId().equals(userId)) {
                throw new UnauthorizedBookingOperationException("User is not authorized to access this booking");
            }
            if (!booking.getRoom().getLocation().getId().equals(user.getLocation().getId())) {
                throw new UnauthorizedLocationAccessException("User is not authorized to access bookings in another location");
            }
        }

        return BookingResponse.fromEntity(booking, clock);
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> getMyBookings(Long userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        Pageable validatedPageable = sanitizePageable(pageable);
        Page<Booking> bookingPage = bookingRepository.findAllByUserId(userId, validatedPageable);
        return PageResponse.fromPage(bookingPage, b -> BookingResponse.fromEntity(b, clock));
    }

    @Transactional
    public BookingResponse rescheduleBooking(Long bookingId, Long userId, RescheduleBookingRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (user.getRole() == UserRole.USER) {
            if (!booking.getUser().getId().equals(userId)) {
                throw new UnauthorizedBookingOperationException("User is not authorized to modify this booking");
            }
            if (!booking.getRoom().getLocation().getId().equals(user.getLocation().getId())) {
                throw new UnauthorizedLocationAccessException("User is not authorized to modify bookings in another location");
            }
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingAlreadyCancelledException(bookingId);
        }

        if (booking.getStatus() == BookingStatus.COMPLETED || isEffectivelyCompleted(booking)) {
            throw new BookingAlreadyCompletedException(bookingId);
        }

        // Step 5: Acquire PESSIMISTIC_WRITE lock on the room to serialize concurrent reschedules/bookings
        Room room = roomRepository.findByIdForUpdate(booking.getRoom().getId())
                .orElseThrow(() -> new RoomNotFoundException(booking.getRoom().getId()));

        if (!room.getLocation().isActive()) {
            throw new LocationNotActiveException("Location is inactive and cannot be rescheduled");
        }

        if (!room.isActive()) {
            throw new RoomNotBookableException("Room is inactive and cannot be rescheduled");
        }

        if (room.getStatus() == RoomStatus.LOCKED) {
            throw new RoomNotBookableException("Room is currently locked and cannot be rescheduled");
        }

        validateBookingTimes(request.getStartTime(), request.getEndTime());

        if (bookingRepository.existsOverlappingBookingExcludingBooking(
                room.getId(), bookingId, request.getStartTime(), request.getEndTime())) {
            throw new BookingOverlapException("The requested rescheduled time slot overlaps with an existing confirmed booking");
        }

        booking.setStartTime(request.getStartTime());
        booking.setEndTime(request.getEndTime());

        Booking updatedBooking = bookingRepository.save(booking);
        log.info("Rescheduled booking id: {} for user: {}", updatedBooking.getId(), userId);
        return BookingResponse.fromEntity(updatedBooking, clock);
    }

    @Transactional
    public void cancelBooking(Long bookingId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (user.getRole() == UserRole.USER) {
            if (!booking.getUser().getId().equals(userId)) {
                throw new UnauthorizedBookingOperationException("User is not authorized to cancel this booking");
            }
            if (!booking.getRoom().getLocation().getId().equals(user.getLocation().getId())) {
                throw new UnauthorizedLocationAccessException("User is not authorized to cancel bookings in another location");
            }
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingAlreadyCancelledException(bookingId);
        }

        if (booking.getStatus() == BookingStatus.COMPLETED || isEffectivelyCompleted(booking)) {
            throw new BookingAlreadyCompletedException(bookingId);
        }

        // Acquire PESSIMISTIC_WRITE lock on Room for uniform lock ordering
        roomRepository.findByIdForUpdate(booking.getRoom().getId())
                .orElseThrow(() -> new RoomNotFoundException(booking.getRoom().getId()));

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        log.info("Cancelled booking id: {} for user: {}", bookingId, userId);
    }

    private void validateBookingTimes(OffsetDateTime startTime, OffsetDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new InvalidBookingTimeException("Start time and end time are required");
        }

        if (!startTime.isBefore(endTime)) {
            throw new InvalidBookingTimeException("Start time must be strictly before end time");
        }

        Duration duration = Duration.between(startTime, endTime);
        if (duration.toMinutes() < 15) {
            throw new InvalidBookingTimeException("Booking duration must be at least 15 minutes");
        }

        if (startTime.getMinute() % 15 != 0 || startTime.getSecond() != 0 || startTime.getNano() != 0) {
            throw new InvalidBookingTimeException("Booking start time must be aligned to a 15-minute boundary with zero seconds and nanoseconds");
        }

        if (endTime.getMinute() % 15 != 0 || endTime.getSecond() != 0 || endTime.getNano() != 0) {
            throw new InvalidBookingTimeException("Booking end time must be aligned to a 15-minute boundary with zero seconds and nanoseconds");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (startTime.isBefore(now)) {
            throw new InvalidBookingTimeException("Booking start time cannot be in the past");
        }
    }

    private boolean isEffectivelyCompleted(Booking booking) {
        return booking.getStatus() == BookingStatus.CONFIRMED && booking.getEndTime().isBefore(OffsetDateTime.now(clock));
    }

    private Pageable sanitizePageable(Pageable pageable) {
        int page = Math.max(pageable.getPageNumber(), 0);
        int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE);

        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Direction.DESC, "startTime");
        } else {
            for (Sort.Order order : sort) {
                if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                    throw new IllegalArgumentException(
                            String.format("Invalid sort property '%s'. Allowed properties: %s",
                                    order.getProperty(), ALLOWED_SORT_FIELDS)
                    );
                }
            }
        }

        return PageRequest.of(page, size, sort);
    }
}
