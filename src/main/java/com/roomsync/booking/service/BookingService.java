package com.roomsync.booking.service;

import com.roomsync.audit.service.AuditService;
import com.roomsync.booking.dto.BookingResponse;
import com.roomsync.booking.dto.CreateBookingRequest;
import com.roomsync.booking.dto.RescheduleBookingRequest;
import com.roomsync.booking.entity.Booking;
import com.roomsync.booking.entity.BookingStatus;
import com.roomsync.booking.exception.BookingAlreadyCancelledException;
import com.roomsync.booking.exception.BookingAlreadyCompletedException;
import com.roomsync.booking.exception.BookingNotFoundException;
import com.roomsync.booking.exception.BookingOverlapException;
import com.roomsync.booking.exception.RoomNotBookableException;
import com.roomsync.booking.exception.UnauthorizedBookingOperationException;
import com.roomsync.booking.repository.BookingRepository;
import com.roomsync.common.response.PageResponse;
import com.roomsync.common.time.TimeService;
import com.roomsync.common.time.TimezoneService;
import com.roomsync.location.exception.LocationNotActiveException;
import com.roomsync.location.exception.UnauthorizedLocationAccessException;
import com.roomsync.notification.service.NotificationOutboxService;
import com.roomsync.room.entity.Room;
import com.roomsync.room.entity.RoomStatus;
import com.roomsync.room.exception.RoomNotFoundException;
import com.roomsync.room.repository.RoomRepository;
import com.roomsync.user.entity.User;
import com.roomsync.user.entity.UserRole;
import com.roomsync.user.exception.UserBookingBlockedException;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service managing Booking lifecycle, business rules, location boundaries, concurrency protection,
 * and transactional audit & outbox integration.
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
    private final TimeService timeService;
    private final TimezoneService timezoneService;
    private final BookingConcurrencyService bookingConcurrencyService;
    private final AuditService auditService;
    private final NotificationOutboxService notificationOutboxService;
    private final BookingCompletionService bookingCompletionService;
    private final Clock clock;

    /**
     * Creates a new booking within a transactional boundary synchronized via deterministic PostgreSQL
     * transaction-scoped advisory locks on all affected (room_id, local_date) keys and a PESSIMISTIC_WRITE lock on Room.
     * Atomically persists Booking, AuditLog, and NotificationOutbox (PENDING).
     */
    @Transactional
    public BookingResponse createBooking(Long userId, CreateBookingRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!user.isBookingEnabled()) {
            throw new UserBookingBlockedException("Your account is currently restricted from creating room bookings. Please contact an administrator.");
        }

        Room room = roomRepository.findByIdForUpdate(request.getRoomId())
                .orElseThrow(() -> new RoomNotFoundException(request.getRoomId()));

        if (!room.getLocation().isActive()) {
            throw new LocationNotActiveException(
                    String.format("Location '%s' is inactive and room cannot be booked", room.getLocation().getName())
            );
        }

        if (user.getRoleEnum() == UserRole.USER && !room.getLocation().getId().equals(user.getLocation().getId())) {
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

        // 1. Resolve authoritative location timezone and validate temporal bounds
        ZoneId zoneId = timezoneService.getLocationZoneId(room.getLocation());
        timeService.validateBookingTimes(request.getStartTime(), request.getEndTime());

        // 2. Acquire deterministic transaction-scoped PostgreSQL advisory locks on all affected (room_id, local_date) keys
        bookingConcurrencyService.acquireLocksForBooking(room.getId(), request.getStartTime(), request.getEndTime(), zoneId);

        // 3. Perform friendly application-level overlap check
        if (bookingRepository.existsOverlappingBooking(room.getId(), request.getStartTime(), request.getEndTime())) {
            throw new BookingOverlapException("The requested time slot overlaps with an existing confirmed booking for this room");
        }

        Booking booking = Booking.builder()
                .room(room)
                .user(user)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .reason(request.getReason())
                .status(BookingStatus.CONFIRMED)
                .build();

        Booking savedBooking = bookingRepository.save(booking);

        // Atomically persist Audit record & Notification Outbox event inside same transaction
        auditService.logBookingAction("BOOKING_CREATED", savedBooking, userId, Map.of("reason", request.getReason()));
        notificationOutboxService.createBookingEvent("BOOKING_CONFIRMED", savedBooking, Map.of("action", "CREATE"));

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

        if (user.getRoleEnum() == UserRole.USER) {
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

        if (!user.isBookingEnabled()) {
            throw new UserBookingBlockedException("Your account is currently restricted from rescheduling room bookings. Please contact an administrator.");
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (user.getRoleEnum() == UserRole.USER) {
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

        // Determine old and new room
        Room oldRoom = booking.getRoom();
        Long targetRoomId = (request.getRoomId() != null) ? request.getRoomId() : oldRoom.getId();
        Room newRoom;
        if (targetRoomId.equals(oldRoom.getId())) {
            newRoom = oldRoom;
        } else {
            newRoom = roomRepository.findById(targetRoomId)
                    .orElseThrow(() -> new RoomNotFoundException(targetRoomId));
        }

        if (!newRoom.getLocation().isActive()) {
            throw new LocationNotActiveException("Location is inactive and cannot be rescheduled");
        }

        if (user.getRoleEnum() == UserRole.USER && !newRoom.getLocation().getId().equals(user.getLocation().getId())) {
            throw new UnauthorizedLocationAccessException("User cannot reschedule to a room in another location");
        }

        if (!newRoom.isActive()) {
            throw new RoomNotBookableException("Room is inactive and cannot be rescheduled");
        }

        if (newRoom.getStatus() == RoomStatus.LOCKED) {
            throw new RoomNotBookableException("Room is currently locked and cannot be rescheduled");
        }

        // 1. Resolve authoritative location timezones and validate temporal bounds
        ZoneId oldZoneId = timezoneService.getLocationZoneId(oldRoom.getLocation());
        ZoneId newZoneId = timezoneService.getLocationZoneId(newRoom.getLocation());
        timeService.validateBookingTimes(request.getStartTime(), request.getEndTime());

        // 2. Generate ALL advisory lock keys across old and new intervals, sort globally, and acquire
        bookingConcurrencyService.acquireLocksForReschedule(
                oldRoom.getId(), booking.getStartTime(), booking.getEndTime(), oldZoneId,
                newRoom.getId(), request.getStartTime(), request.getEndTime(), newZoneId
        );

        // 3. Acquire PESSIMISTIC_WRITE locks on room rows in strictly ascending room ID order to prevent deadlock
        if (oldRoom.getId().equals(newRoom.getId())) {
            roomRepository.findByIdForUpdate(oldRoom.getId())
                    .orElseThrow(() -> new RoomNotFoundException(oldRoom.getId()));
        } else if (oldRoom.getId() < newRoom.getId()) {
            roomRepository.findByIdForUpdate(oldRoom.getId())
                    .orElseThrow(() -> new RoomNotFoundException(oldRoom.getId()));
            roomRepository.findByIdForUpdate(newRoom.getId())
                    .orElseThrow(() -> new RoomNotFoundException(newRoom.getId()));
        } else {
            roomRepository.findByIdForUpdate(newRoom.getId())
                    .orElseThrow(() -> new RoomNotFoundException(newRoom.getId()));
            roomRepository.findByIdForUpdate(oldRoom.getId())
                    .orElseThrow(() -> new RoomNotFoundException(oldRoom.getId()));
        }

        // 4. Soft-cancel original booking: status = CANCELLED, cancelled_reason = RESCHEDULED
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledReason("RESCHEDULED");
        bookingRepository.save(booking);
        bookingRepository.flush();

        // 5. Perform friendly application-level overlap check for the replacement booking
        if (bookingRepository.existsOverlappingBooking(newRoom.getId(), request.getStartTime(), request.getEndTime())) {
            throw new BookingOverlapException("The requested rescheduled time slot overlaps with an existing confirmed booking");
        }

        // 6. Create and persist replacement booking with rescheduledFrom = original
        Booking replacementBooking = Booking.builder()
                .room(newRoom)
                .user(booking.getUser())
                .series(booking.getSeries())
                .occurrenceIndex(booking.getOccurrenceIndex())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .reason(booking.getReason())
                .rescheduledFrom(booking)
                .status(BookingStatus.CONFIRMED)
                .build();

        Booking savedReplacement = bookingRepository.save(replacementBooking);

        // Atomically log audit for original cancellation and replacement, and create outbox event
        auditService.logBookingAction("BOOKING_CANCELLED", booking, userId,
                Map.of("cancelledReason", "RESCHEDULED", "replacementBookingId", savedReplacement.getId()));
        auditService.logBookingAction("BOOKING_RESCHEDULED", savedReplacement, userId,
                Map.of("originalBookingId", booking.getId(), "oldRoomId", oldRoom.getId(), "newRoomId", newRoom.getId(),
                        "oldStartTime", booking.getStartTime().toString(), "newStartTime", request.getStartTime().toString()));
        notificationOutboxService.createBookingEvent("BOOKING_RESCHEDULED", savedReplacement,
                Map.of("originalBookingId", booking.getId()));

        log.info("Rescheduled booking id: {} -> replacement id: {} for user: {}",
                booking.getId(), savedReplacement.getId(), userId);
        return BookingResponse.fromEntity(savedReplacement, clock);
    }

    @Transactional
    public void cancelBooking(Long bookingId, Long userId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (user.getRoleEnum() == UserRole.USER) {
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
        String finalReason = (reason != null && !reason.trim().isEmpty())
                ? reason.trim()
                : (user.getRoleEnum() == UserRole.ADMIN ? "Cancelled by admin" : "Cancelled by user");
        booking.setCancelledReason(finalReason);
        Booking saved = bookingRepository.save(booking);

        // Atomically record audit and outbox event
        auditService.logBookingAction("BOOKING_CANCELLED", saved, userId, Map.of("cancelledReason", finalReason));
        notificationOutboxService.createBookingEvent("BOOKING_CANCELLED", saved, Map.of("cancelledReason", finalReason));

        log.info("Cancelled booking id: {} for user: {} with reason: '{}'", bookingId, userId, finalReason);
    }

    @Transactional
    public void cancelBooking(Long bookingId, Long userId) {
        cancelBooking(bookingId, userId, null);
    }

    /**
     * Finds confirmed bookings whose end time has passed and transitions them to COMPLETED
     * using per-booking independent transactions and creating Audit & NotificationOutbox events.
     * Safe to execute repeatedly (idempotent).
     */
    @Transactional
    public int completePastBookings() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Long> pastBookingIds = bookingRepository.findPastConfirmedBookingIds(now);
        if (pastBookingIds != null && !pastBookingIds.isEmpty()) {
            int updatedCount = 0;
            for (Long bookingId : pastBookingIds) {
                try {
                    if (bookingCompletionService.completeSingleBooking(bookingId)) {
                        updatedCount++;
                    }
                } catch (Exception ex) {
                    log.error("Failed to complete booking id: {}", bookingId, ex);
                }
            }
            if (updatedCount > 0) {
                log.info("Auto-completed {} past confirmed bookings at {}", updatedCount, now);
            }
            return updatedCount;
        }
        int bulkCount = bookingRepository.completePastConfirmedBookings(now);
        if (bulkCount > 0) {
            log.info("Auto-completed {} past confirmed bookings at {}", bulkCount, now);
        }
        return bulkCount;
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
