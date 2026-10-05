package com.roomsync.booking.service;

import com.roomsync.booking.dto.RecurringOccurrenceResult;
import com.roomsync.booking.entity.Booking;
import com.roomsync.booking.entity.BookingSeries;
import com.roomsync.booking.entity.BookingSeriesStatus;
import com.roomsync.booking.entity.BookingStatus;
import com.roomsync.booking.entity.RecurrenceFrequency;
import com.roomsync.booking.exception.BookingNotFoundException;
import com.roomsync.booking.exception.BookingOverlapException;
import com.roomsync.booking.exception.RoomNotBookableException;
import com.roomsync.booking.repository.BookingRepository;
import com.roomsync.booking.repository.BookingSeriesRepository;
import com.roomsync.common.time.BookingInterval;
import com.roomsync.common.time.TimeService;
import com.roomsync.common.time.TimezoneService;
import com.roomsync.location.entity.Location;
import com.roomsync.location.exception.LocationNotActiveException;
import com.roomsync.room.entity.Room;
import com.roomsync.room.entity.RoomStatus;
import com.roomsync.room.exception.RoomNotFoundException;
import com.roomsync.room.repository.RoomRepository;
import com.roomsync.user.entity.User;
import com.roomsync.user.exception.UserNotFoundException;
import com.roomsync.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringOccurrenceWorker {

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final BookingSeriesRepository bookingSeriesRepository;
    private final com.roomsync.booking.repository.BookingOccurrenceExceptionRepository bookingOccurrenceExceptionRepository;
    private final BookingConcurrencyService bookingConcurrencyService;
    private final TimezoneService timezoneService;
    private final TimeService timeService;
    private final com.roomsync.audit.service.AuditService auditService;
    private final com.roomsync.notification.service.NotificationOutboxService notificationOutboxService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BookingSeries createInitialSeries(
            Long userId,
            Long roomId,
            String seriesName,
            RecurrenceFrequency frequency,
            LocalDate startDate,
            LocalDate endDate,
            Integer occurrenceCount,
            LocalTime startLocalTime,
            LocalTime endLocalTime,
            String daysOfWeek,
            Integer dayOfMonth) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));

        Location location = room.getLocation();

        BookingSeries series = BookingSeries.builder()
                .user(user)
                .seriesName(seriesName)
                .frequency(frequency)
                .startDate(startDate)
                .endDate(endDate)
                .occurrenceCount(occurrenceCount)
                .startLocalTime(startLocalTime)
                .endLocalTime(endLocalTime)
                .timezone(location.getTimezone())
                .daysOfWeek(daysOfWeek)
                .dayOfMonth(dayOfMonth)
                .status(BookingSeriesStatus.ACTIVE)
                .build();

        BookingSeries savedSeries = bookingSeriesRepository.saveAndFlush(series);
        log.info("Committed initial BookingSeries id: {} for user: {}", savedSeries.getId(), user.getId());
        return savedSeries;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RecurringOccurrenceResult processOccurrence(
            Long seriesId,
            int occurrenceIndex,
            LocalDate date,
            Long roomId,
            String reason) {

        BookingSeries series = bookingSeriesRepository.findById(seriesId)
                .orElseThrow(() -> new BookingNotFoundException(seriesId));

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));

        if (!room.getLocation().isActive()) {
            throw new LocationNotActiveException(
                    String.format("Location '%s' is inactive and room cannot be booked", room.getLocation().getName())
            );
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

        // 1. Resolve authoritative location timezone
        ZoneId zoneId = timezoneService.getLocationZoneId(room.getLocation());

        // 2. Validate occurrence time and calculate UTC interval
        BookingInterval interval = timeService.calculateBookingInterval(
                date,
                series.getStartLocalTime(),
                series.getEndLocalTime(),
                zoneId
        );

        // Check if occurrence was explicitly skipped/excepted
        Optional<com.roomsync.booking.entity.BookingOccurrenceException> exceptionOpt =
                bookingOccurrenceExceptionRepository.findBySeriesIdAndOccurrenceIndex(seriesId, occurrenceIndex);
        if (exceptionOpt.isPresent()) {
            com.roomsync.booking.entity.BookingOccurrenceException ex = exceptionOpt.get();
            log.info("Occurrence {} for series {} has exception {}, skipping booking creation",
                    occurrenceIndex, seriesId, ex.getExceptionType());
            return RecurringOccurrenceResult.builder()
                    .occurrenceIndex(occurrenceIndex)
                    .date(date)
                    .status(ex.getExceptionType().name())
                    .roomId(room.getId())
                    .roomName(room.getName())
                    .startTime(interval.startUtc())
                    .endTime(interval.endUtc())
                    .reason(ex.getReason())
                    .conflictReason(null)
                    .build();
        }

        // 3. Determine all affected local dates across the booking interval
        List<LocalDate> affectedDates = bookingConcurrencyService.calculateAffectedLocalDates(
                interval.startUtc(),
                interval.endUtc(),
                zoneId
        );

        // 4. Generate deterministic advisory lock keys and acquire pg_advisory_xact_lock in ascending order
        List<Long> lockKeys = bookingConcurrencyService.generateDeterministicLockKeys(room.getId(), affectedDates);
        bookingConcurrencyService.acquireAdvisoryLocks(lockKeys);

        // 5. Perform friendly application-level overlap check
        if (bookingRepository.existsOverlappingBooking(room.getId(), interval.startUtc(), interval.endUtc())) {
            throw new BookingOverlapException("The requested time slot overlaps with an existing confirmed booking");
        }

        // 6. Persist Booking with series relationship and occurrence_index
        Booking booking = Booking.builder()
                .room(room)
                .user(series.getUser())
                .series(series)
                .occurrenceIndex(occurrenceIndex)
                .startTime(interval.startUtc())
                .endTime(interval.endUtc())
                .reason(reason)
                .status(BookingStatus.CONFIRMED)
                .build();

        Booking savedBooking = bookingRepository.saveAndFlush(booking);

        auditService.logBookingAction("BOOKING_CREATED", savedBooking, series.getUser().getId(),
                java.util.Map.of("seriesId", series.getId(), "occurrenceIndex", occurrenceIndex));
        notificationOutboxService.createBookingEvent("BOOKING_CONFIRMED", savedBooking,
                java.util.Map.of("seriesId", series.getId(), "occurrenceIndex", occurrenceIndex));

        log.info("Persisted recurring occurrence booking id: {} for series: {} (occurrence: {}) in room: {}",
                savedBooking.getId(), series.getId(), occurrenceIndex, room.getId());

        return RecurringOccurrenceResult.builder()
                .occurrenceIndex(occurrenceIndex)
                .date(date)
                .bookingId(savedBooking.getId())
                .status("CONFIRMED")
                .roomId(room.getId())
                .roomName(room.getName())
                .startTime(interval.startUtc())
                .endTime(interval.endUtc())
                .reason(reason)
                .conflictReason(null)
                .build();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateSeriesStatus(Long seriesId, BookingSeriesStatus status) {
        BookingSeries series = bookingSeriesRepository.findById(seriesId)
                .orElseThrow(() -> new BookingNotFoundException(seriesId));
        series.setStatus(status);
        bookingSeriesRepository.saveAndFlush(series);
        log.info("Updated series id: {} status to: {}", seriesId, status);
    }
}
