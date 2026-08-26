package com.roomsync.booking.service;

import com.roomsync.admin.dto.AdminRequestResponse;
import com.roomsync.admin.entity.AdminRequest;
import com.roomsync.admin.entity.AdminRequestStatus;
import com.roomsync.admin.repository.AdminRequestRepository;
import com.roomsync.audit.service.AuditService;
import com.roomsync.booking.dto.BookingResponse;
import com.roomsync.booking.dto.ContactAdminOccurrenceRequest;
import com.roomsync.booking.dto.CreateRecurringBookingRequest;
import com.roomsync.booking.dto.RecurringConfirmationResponse;
import com.roomsync.booking.dto.RecurringOccurrencePreview;
import com.roomsync.booking.dto.RecurringOccurrenceResult;
import com.roomsync.booking.dto.RecurringPreviewResponse;
import com.roomsync.booking.dto.RecurringSeriesResponse;
import com.roomsync.booking.dto.ResolveAlternateRoomRequest;
import com.roomsync.booking.entity.Booking;
import com.roomsync.booking.entity.BookingOccurrenceException;
import com.roomsync.booking.entity.BookingSeries;
import com.roomsync.booking.entity.BookingSeriesStatus;
import com.roomsync.booking.entity.BookingStatus;
import com.roomsync.booking.entity.OccurrenceExceptionType;
import com.roomsync.booking.exception.BookingNotFoundException;
import com.roomsync.booking.exception.BookingOverlapException;
import com.roomsync.booking.exception.RoomNotBookableException;
import com.roomsync.booking.exception.UnauthorizedBookingOperationException;
import com.roomsync.booking.repository.BookingOccurrenceExceptionRepository;
import com.roomsync.booking.repository.BookingRepository;
import com.roomsync.booking.repository.BookingSeriesRepository;
import com.roomsync.common.time.BookingInterval;
import com.roomsync.common.time.TimeService;
import com.roomsync.common.time.TimezoneService;
import com.roomsync.location.entity.Location;
import com.roomsync.location.exception.LocationNotActiveException;
import com.roomsync.location.exception.UnauthorizedLocationAccessException;
import com.roomsync.location.repository.LocationRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringBookingService {

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final LocationRepository locationRepository;
    private final BookingSeriesRepository bookingSeriesRepository;
    private final BookingRepository bookingRepository;
    private final BookingOccurrenceExceptionRepository bookingOccurrenceExceptionRepository;
    private final AdminRequestRepository adminRequestRepository;
    private final RecurrenceGenerator recurrenceGenerator;
    private final RecurringConflictService recurringConflictService;
    private final RecurringOccurrenceWorker recurringOccurrenceWorker;
    private final BookingConcurrencyService bookingConcurrencyService;
    private final BookingService bookingService;
    private final AuditService auditService;
    private final TimezoneService timezoneService;
    private final TimeService timeService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public RecurringPreviewResponse previewSeries(Long userId, CreateRecurringBookingRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new RoomNotFoundException(request.getRoomId()));

        Location location = locationRepository.findById(room.getLocation().getId())
                .orElse(room.getLocation());

        if (user.getRoleEnum() == UserRole.USER && !location.getId().equals(user.getLocation().getId())) {
            throw new UnauthorizedLocationAccessException("User cannot preview recurring bookings in another location");
        }

        List<RecurrenceGenerator.OccurrenceDate> occurrenceDates = recurrenceGenerator.generateOccurrences(
                request.getFrequency(),
                request.getStartDate(),
                request.getEndDate(),
                request.getOccurrenceCount(),
                request.getDaysOfWeek(),
                request.getDayOfMonth()
        );

        List<RecurringOccurrencePreview> previews = recurringConflictService.evaluateOccurrences(
                room.getId(),
                request.getStartLocalTime(),
                request.getEndLocalTime(),
                occurrenceDates
        );

        int availableCount = 0;
        int conflictCount = 0;
        for (RecurringOccurrencePreview p : previews) {
            if ("AVAILABLE".equals(p.getAvailability())) {
                availableCount++;
            } else {
                conflictCount++;
            }
        }

        return RecurringPreviewResponse.builder()
                .seriesName(request.getSeriesName())
                .roomId(room.getId())
                .roomName(room.getName())
                .frequency(request.getFrequency())
                .totalOccurrences(previews.size())
                .availableCount(availableCount)
                .conflictCount(conflictCount)
                .occurrences(previews)
                .build();
    }

    public RecurringConfirmationResponse createAndConfirmSeries(Long userId, CreateRecurringBookingRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new RoomNotFoundException(request.getRoomId()));

        Location location = locationRepository.findById(room.getLocation().getId())
                .orElse(room.getLocation());

        if (user.getRoleEnum() == UserRole.USER && !location.getId().equals(user.getLocation().getId())) {
            throw new UnauthorizedLocationAccessException("User cannot create recurring bookings in another location");
        }

        // Validate 15-minute boundary alignment on start and end times
        timeService.validate15MinuteBoundary(request.getStartLocalTime(), "Recurring start time");
        timeService.validate15MinuteBoundary(request.getEndLocalTime(), "Recurring end time");

        List<RecurrenceGenerator.OccurrenceDate> occurrenceDates = recurrenceGenerator.generateOccurrences(
                request.getFrequency(),
                request.getStartDate(),
                request.getEndDate(),
                request.getOccurrenceCount(),
                request.getDaysOfWeek(),
                request.getDayOfMonth()
        );

        String daysOfWeekStr = (request.getDaysOfWeek() != null && !request.getDaysOfWeek().isEmpty())
                ? String.join(",", request.getDaysOfWeek())
                : null;

        BookingSeries savedSeries = recurringOccurrenceWorker.createInitialSeries(
                user.getId(),
                room.getId(),
                request.getSeriesName() != null ? request.getSeriesName() : request.getReason(),
                request.getFrequency(),
                request.getStartDate(),
                request.getEndDate(),
                request.getOccurrenceCount(),
                request.getStartLocalTime(),
                request.getEndLocalTime(),
                daysOfWeekStr,
                request.getDayOfMonth()
        );

        List<RecurringOccurrenceResult> results = new ArrayList<>();
        int confirmedCount = 0;
        int conflictCount = 0;

        for (RecurrenceGenerator.OccurrenceDate occ : occurrenceDates) {
            try {
                RecurringOccurrenceResult result = recurringOccurrenceWorker.processOccurrence(
                        savedSeries.getId(),
                        occ.getOccurrenceIndex(),
                        occ.getDate(),
                        room.getId(),
                        request.getReason()
                );
                results.add(result);
                if ("CONFIRMED".equals(result.getStatus())) {
                    confirmedCount++;
                }
            } catch (Exception ex) {
                log.info("Occurrence {} for series {} had conflict: {}", occ.getOccurrenceIndex(), savedSeries.getId(), ex.getMessage());
                conflictCount++;
                results.add(RecurringOccurrenceResult.builder()
                        .occurrenceIndex(occ.getOccurrenceIndex())
                        .date(occ.getDate())
                        .status("CONFLICT")
                        .roomId(room.getId())
                        .roomName(room.getName())
                        .reason(request.getReason())
                        .conflictReason(ex.getMessage())
                        .build());
            }
        }

        // Determine final series status
        BookingSeriesStatus finalStatus;
        if (conflictCount == 0) {
            finalStatus = BookingSeriesStatus.ACTIVE;
        } else if (confirmedCount > 0) {
            finalStatus = BookingSeriesStatus.PARTIALLY_CONFIRMED;
        } else {
            finalStatus = BookingSeriesStatus.CANCELLED;
        }

        recurringOccurrenceWorker.updateSeriesStatus(savedSeries.getId(), finalStatus);

        return RecurringConfirmationResponse.builder()
                .seriesId(savedSeries.getId())
                .seriesName(savedSeries.getSeriesName())
                .roomId(room.getId())
                .roomName(room.getName())
                .frequency(savedSeries.getFrequency())
                .seriesStatus(finalStatus)
                .totalOccurrences(occurrenceDates.size())
                .confirmedCount(confirmedCount)
                .conflictCount(conflictCount)
                .occurrences(results)
                .build();
    }

    @Transactional
    public void cancelOccurrence(Long seriesId, Integer occurrenceIndex, Long userId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        BookingSeries series = bookingSeriesRepository.findById(seriesId)
                .orElseThrow(() -> new BookingNotFoundException(seriesId));

        if (user.getRoleEnum() == UserRole.USER && !series.getUser().getId().equals(userId)) {
            throw new UnauthorizedBookingOperationException("User is not authorized to cancel occurrences of this series");
        }

        Booking booking = bookingRepository.findBySeriesIdAndOccurrenceIndex(seriesId, occurrenceIndex)
                .orElseThrow(() -> new BookingNotFoundException(
                        String.format("Occurrence #%d for series %d was not found", occurrenceIndex, seriesId)
                ));

        bookingService.cancelBooking(booking.getId(), userId, reason);
    }

    @Transactional
    public RecurringOccurrenceResult skipOccurrence(Long seriesId, Integer occurrenceIndex, Long userId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        BookingSeries series = bookingSeriesRepository.findById(seriesId)
                .orElseThrow(() -> new BookingNotFoundException(seriesId));

        if (user.getRoleEnum() == UserRole.USER && !series.getUser().getId().equals(userId)) {
            throw new UnauthorizedBookingOperationException("User is not authorized to skip occurrences of this series");
        }

        // Validate occurrence index against recurrence bounds
        List<String> daysOfWeekList = (series.getDaysOfWeek() != null && !series.getDaysOfWeek().isEmpty())
                ? List.of(series.getDaysOfWeek().split(","))
                : null;

        List<RecurrenceGenerator.OccurrenceDate> occurrenceDates = recurrenceGenerator.generateOccurrences(
                series.getFrequency(),
                series.getStartDate(),
                series.getEndDate(),
                series.getOccurrenceCount(),
                daysOfWeekList,
                series.getDayOfMonth()
        );

        RecurrenceGenerator.OccurrenceDate targetOcc = occurrenceDates.stream()
                .filter(o -> o.getOccurrenceIndex() == occurrenceIndex)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid occurrence index for series: " + occurrenceIndex));

        // Ensure occurrence does not already have a confirmed booking
        Optional<Booking> existingBookingOpt = bookingRepository.findBySeriesIdAndOccurrenceIndex(seriesId, occurrenceIndex);
        if (existingBookingOpt.isPresent() && existingBookingOpt.get().getStatus() == BookingStatus.CONFIRMED) {
            throw new BookingOverlapException("Cannot skip an occurrence that is already confirmed as a booking");
        }

        // Idempotency: if already skipped, return existing skipped record
        Optional<BookingOccurrenceException> existingExceptionOpt =
                bookingOccurrenceExceptionRepository.findBySeriesIdAndOccurrenceIndex(seriesId, occurrenceIndex);

        BookingOccurrenceException exception;
        if (existingExceptionOpt.isPresent()) {
            exception = existingExceptionOpt.get();
        } else {
            String skipReason = (reason != null && !reason.trim().isEmpty()) ? reason.trim() : "Skipped by user";
            exception = BookingOccurrenceException.builder()
                    .series(series)
                    .occurrenceIndex(occurrenceIndex)
                    .exceptionType(OccurrenceExceptionType.SKIPPED)
                    .reason(skipReason)
                    .createdByUser(user)
                    .build();
            exception = bookingOccurrenceExceptionRepository.save(exception);

            auditService.logBookingAction(
                    "RECURRING_OCCURRENCE_SKIPPED",
                    null,
                    userId,
                    Map.of(
                            "seriesId", seriesId,
                            "occurrenceIndex", occurrenceIndex,
                            "reason", skipReason
                    )
            );
        }

        // Re-evaluate series status
        List<Booking> allBookings = bookingRepository.findAllBySeriesIdOrderByOccurrenceIndexAsc(seriesId);
        List<BookingOccurrenceException> allExceptions = bookingOccurrenceExceptionRepository.findAllBySeriesIdOrderByOccurrenceIndexAsc(seriesId);

        Set<Integer> confirmedIndices = allBookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .map(Booking::getOccurrenceIndex)
                .collect(Collectors.toSet());

        Set<Integer> skippedIndices = allExceptions.stream()
                .filter(e -> e.getExceptionType() == OccurrenceExceptionType.SKIPPED)
                .map(BookingOccurrenceException::getOccurrenceIndex)
                .collect(Collectors.toSet());

        boolean allResolved = true;
        for (RecurrenceGenerator.OccurrenceDate occ : occurrenceDates) {
            if (!confirmedIndices.contains(occ.getOccurrenceIndex()) && !skippedIndices.contains(occ.getOccurrenceIndex())) {
                allResolved = false;
                break;
            }
        }

        if (allResolved && series.getStatus() == BookingSeriesStatus.PARTIALLY_CONFIRMED) {
            series.setStatus(BookingSeriesStatus.ACTIVE);
            bookingSeriesRepository.save(series);
        }

        Long roomId = allBookings.isEmpty() ? null : allBookings.get(0).getRoom().getId();
        String roomName = allBookings.isEmpty() ? null : allBookings.get(0).getRoom().getName();

        ZoneId zoneId = ZoneId.of(series.getTimezone());
        BookingInterval interval = timeService.calculateBookingInterval(
                targetOcc.getDate(),
                series.getStartLocalTime(),
                series.getEndLocalTime(),
                zoneId
        );

        log.info("Recorded persistent skip for occurrence #{} of series #{}", occurrenceIndex, seriesId);

        return RecurringOccurrenceResult.builder()
                .occurrenceIndex(occurrenceIndex)
                .date(targetOcc.getDate())
                .status("SKIPPED")
                .roomId(roomId)
                .roomName(roomName)
                .startTime(interval.startUtc())
                .endTime(interval.endUtc())
                .reason(exception.getReason())
                .conflictReason(null)
                .build();
    }

    @Transactional
    public BookingResponse resolveOccurrenceWithAlternateRoom(
            Long seriesId,
            Integer occurrenceIndex,
            Long userId,
            Long alternateRoomId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        BookingSeries series = bookingSeriesRepository.findById(seriesId)
                .orElseThrow(() -> new BookingNotFoundException(seriesId));

        if (user.getRoleEnum() == UserRole.USER && !series.getUser().getId().equals(userId)) {
            throw new UnauthorizedBookingOperationException("User is not authorized to resolve occurrences of this series");
        }

        if (bookingOccurrenceExceptionRepository.existsBySeriesIdAndOccurrenceIndex(seriesId, occurrenceIndex)) {
            throw new BookingOverlapException("Cannot book alternate room for a skipped occurrence");
        }

        Optional<Booking> existingBookingOpt = bookingRepository.findBySeriesIdAndOccurrenceIndex(seriesId, occurrenceIndex);
        if (existingBookingOpt.isPresent() && existingBookingOpt.get().getStatus() == BookingStatus.CONFIRMED) {
            throw new BookingOverlapException("Occurrence is already confirmed");
        }

        // Generate occurrence dates to find exact date for occurrenceIndex
        List<String> daysOfWeekList = (series.getDaysOfWeek() != null && !series.getDaysOfWeek().isEmpty())
                ? List.of(series.getDaysOfWeek().split(","))
                : null;

        List<RecurrenceGenerator.OccurrenceDate> occurrenceDates = recurrenceGenerator.generateOccurrences(
                series.getFrequency(),
                series.getStartDate(),
                series.getEndDate(),
                series.getOccurrenceCount(),
                daysOfWeekList,
                series.getDayOfMonth()
        );

        RecurrenceGenerator.OccurrenceDate targetOcc = occurrenceDates.stream()
                .filter(o -> o.getOccurrenceIndex() == occurrenceIndex)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid occurrence index for series: " + occurrenceIndex));

        Room alternateRoom = roomRepository.findById(alternateRoomId)
                .orElseThrow(() -> new RoomNotFoundException(alternateRoomId));

        Location altLocation = locationRepository.findById(alternateRoom.getLocation().getId())
                .orElse(alternateRoom.getLocation());

        if (!altLocation.isActive()) {
            throw new LocationNotActiveException("Alternate room location is inactive");
        }

        if (user.getRoleEnum() == UserRole.USER && !altLocation.getId().equals(user.getLocation().getId())) {
            throw new UnauthorizedLocationAccessException("User cannot book alternate room in another location");
        }

        if (!alternateRoom.isActive()) {
            throw new RoomNotBookableException("Alternate room is inactive");
        }

        if (alternateRoom.getStatus() == RoomStatus.LOCKED) {
            throw new RoomNotBookableException("Alternate room is currently locked");
        }

        ZoneId zoneId = timezoneService.getLocationZoneId(altLocation);
        BookingInterval interval = timeService.calculateBookingInterval(
                targetOcc.getDate(),
                series.getStartLocalTime(),
                series.getEndLocalTime(),
                zoneId
        );

        List<LocalDate> affectedDates = bookingConcurrencyService.calculateAffectedLocalDates(
                interval.startUtc(),
                interval.endUtc(),
                zoneId
        );

        List<Long> lockKeys = bookingConcurrencyService.generateDeterministicLockKeys(alternateRoom.getId(), affectedDates);
        bookingConcurrencyService.acquireAdvisoryLocks(lockKeys);

        if (bookingRepository.existsOverlappingBooking(alternateRoom.getId(), interval.startUtc(), interval.endUtc())) {
            throw new BookingOverlapException("The requested alternate room has a conflicting booking during this interval");
        }

        Booking booking;
        if (existingBookingOpt.isPresent()) {
            booking = existingBookingOpt.get();
            booking.setRoom(alternateRoom);
            booking.setStartTime(interval.startUtc());
            booking.setEndTime(interval.endUtc());
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setCancelledReason(null);
        } else {
            booking = Booking.builder()
                    .room(alternateRoom)
                    .user(series.getUser())
                    .series(series)
                    .occurrenceIndex(occurrenceIndex)
                    .startTime(interval.startUtc())
                    .endTime(interval.endUtc())
                    .reason(series.getSeriesName())
                    .status(BookingStatus.CONFIRMED)
                    .build();
        }

        Booking savedBooking = bookingRepository.save(booking);

        // If series was PARTIALLY_CONFIRMED, check if all occurrences are now resolved
        if (series.getStatus() == BookingSeriesStatus.PARTIALLY_CONFIRMED) {
            List<Booking> allBookings = bookingRepository.findAllBySeriesIdOrderByOccurrenceIndexAsc(seriesId);
            long confirmedCount = allBookings.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).count();
            if (confirmedCount == occurrenceDates.size()) {
                series.setStatus(BookingSeriesStatus.ACTIVE);
                bookingSeriesRepository.save(series);
            }
        }

        log.info("Resolved occurrence #{} for series {} on alternate room {}", occurrenceIndex, seriesId, alternateRoomId);
        return BookingResponse.fromEntity(savedBooking, clock);
    }

    @Transactional
    public AdminRequestResponse contactAdminForOccurrence(
            Long seriesId,
            Integer occurrenceIndex,
            Long userId,
            ContactAdminOccurrenceRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        BookingSeries series = bookingSeriesRepository.findById(seriesId)
                .orElseThrow(() -> new BookingNotFoundException(seriesId));

        if (user.getRoleEnum() == UserRole.USER && !series.getUser().getId().equals(userId)) {
            throw new UnauthorizedBookingOperationException("User is not authorized to escalate occurrences of this series");
        }

        if (bookingOccurrenceExceptionRepository.existsBySeriesIdAndOccurrenceIndex(seriesId, occurrenceIndex)) {
            throw new BookingOverlapException("Cannot request admin assistance for a skipped occurrence");
        }

        Optional<Booking> bookingOpt = bookingRepository.findBySeriesIdAndOccurrenceIndex(seriesId, occurrenceIndex);

        AdminRequest adminRequest = AdminRequest.builder()
                .requesterUser(user)
                .location(user.getLocation())
                .room(bookingOpt.map(Booking::getRoom).orElse(null))
                .bookingSeries(series)
                .booking(bookingOpt.orElse(null))
                .requestType("RECURRING_CONFLICT")
                .message(String.format("[Series #%d: %s | Occurrence #%d] %s",
                        series.getId(), series.getSeriesName(), occurrenceIndex, request.getMessage()))
                .status(AdminRequestStatus.OPEN)
                .build();

        AdminRequest savedRequest = adminRequestRepository.save(adminRequest);
        log.info("Created AdminRequest id: {} for series #{} occurrence #{} by user: {}",
                savedRequest.getId(), seriesId, occurrenceIndex, userId);

        return AdminRequestResponse.fromEntity(savedRequest);
    }

    @Transactional(readOnly = true)
    public RecurringSeriesResponse getSeries(Long seriesId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        BookingSeries series = bookingSeriesRepository.findById(seriesId)
                .orElseThrow(() -> new BookingNotFoundException(seriesId));

        if (user.getRoleEnum() == UserRole.USER && !series.getUser().getId().equals(userId)) {
            throw new UnauthorizedBookingOperationException("User is not authorized to view this recurring series");
        }

        List<Booking> bookings = bookingRepository.findAllBySeriesIdOrderByOccurrenceIndexAsc(seriesId);
        List<BookingOccurrenceException> exceptions = bookingOccurrenceExceptionRepository.findAllBySeriesIdOrderByOccurrenceIndexAsc(seriesId);

        List<BookingResponse> bookingResponses = bookings.stream()
                .map(b -> BookingResponse.fromEntity(b, clock))
                .toList();

        List<String> daysOfWeekList = (series.getDaysOfWeek() != null && !series.getDaysOfWeek().isEmpty())
                ? List.of(series.getDaysOfWeek().split(","))
                : null;

        List<RecurrenceGenerator.OccurrenceDate> occurrenceDates = recurrenceGenerator.generateOccurrences(
                series.getFrequency(),
                series.getStartDate(),
                series.getEndDate(),
                series.getOccurrenceCount(),
                daysOfWeekList,
                series.getDayOfMonth()
        );

        Map<Integer, Booking> bookingMap = bookings.stream()
                .filter(b -> b.getOccurrenceIndex() != null)
                .collect(Collectors.toMap(
                        Booking::getOccurrenceIndex,
                        b -> b,
                        (b1, b2) -> (b1.getStatus() == BookingStatus.CONFIRMED) ? b1 : (b2.getStatus() == BookingStatus.CONFIRMED ? b2 : (b1.getId() > b2.getId() ? b1 : b2))
                ));

        Map<Integer, BookingOccurrenceException> exceptionMap = exceptions.stream()
                .collect(Collectors.toMap(BookingOccurrenceException::getOccurrenceIndex, e -> e, (e1, e2) -> e1));

        ZoneId zoneId = ZoneId.of(series.getTimezone());

        List<RecurringOccurrenceResult> occurrenceResults = new ArrayList<>();
        int confirmedCount = 0;
        int conflictCount = 0;
        int skippedCount = 0;

        for (RecurrenceGenerator.OccurrenceDate occ : occurrenceDates) {
            int idx = occ.getOccurrenceIndex();
            Booking b = bookingMap.get(idx);
            BookingOccurrenceException ex = exceptionMap.get(idx);

            BookingInterval interval = timeService.calculateBookingInterval(
                    occ.getDate(),
                    series.getStartLocalTime(),
                    series.getEndLocalTime(),
                    zoneId
            );

            if (b != null) {
                if (b.getStatus() == BookingStatus.CONFIRMED) {
                    confirmedCount++;
                }
                occurrenceResults.add(RecurringOccurrenceResult.builder()
                        .occurrenceIndex(idx)
                        .date(occ.getDate())
                        .bookingId(b.getId())
                        .status(b.getStatus().name())
                        .roomId(b.getRoom().getId())
                        .roomName(b.getRoom().getName())
                        .startTime(b.getStartTime())
                        .endTime(b.getEndTime())
                        .reason(b.getReason())
                        .conflictReason(b.getCancelledReason())
                        .build());
            } else if (ex != null) {
                skippedCount++;
                occurrenceResults.add(RecurringOccurrenceResult.builder()
                        .occurrenceIndex(idx)
                        .date(occ.getDate())
                        .status("SKIPPED")
                        .roomId(bookings.isEmpty() ? null : bookings.get(0).getRoom().getId())
                        .roomName(bookings.isEmpty() ? null : bookings.get(0).getRoom().getName())
                        .startTime(interval.startUtc())
                        .endTime(interval.endUtc())
                        .reason(ex.getReason())
                        .conflictReason(null)
                        .build());
            } else {
                conflictCount++;
                occurrenceResults.add(RecurringOccurrenceResult.builder()
                        .occurrenceIndex(idx)
                        .date(occ.getDate())
                        .status("CONFLICT")
                        .roomId(bookings.isEmpty() ? null : bookings.get(0).getRoom().getId())
                        .roomName(bookings.isEmpty() ? null : bookings.get(0).getRoom().getName())
                        .startTime(interval.startUtc())
                        .endTime(interval.endUtc())
                        .reason(series.getSeriesName())
                        .conflictReason("Unresolved schedule conflict")
                        .build());
            }
        }

        return RecurringSeriesResponse.builder()
                .id(series.getId())
                .userId(series.getUser().getId())
                .seriesName(series.getSeriesName())
                .frequency(series.getFrequency())
                .startDate(series.getStartDate())
                .endDate(series.getEndDate())
                .occurrenceCount(series.getOccurrenceCount())
                .startLocalTime(series.getStartLocalTime())
                .endLocalTime(series.getEndLocalTime())
                .timezone(series.getTimezone())
                .daysOfWeek(series.getDaysOfWeek())
                .dayOfMonth(series.getDayOfMonth())
                .status(series.getStatus())
                .createdAt(series.getCreatedAt())
                .updatedAt(series.getUpdatedAt())
                .bookings(bookingResponses)
                .occurrences(occurrenceResults)
                .totalOccurrences(occurrenceDates.size())
                .confirmedCount(confirmedCount)
                .conflictCount(conflictCount)
                .skippedCount(skippedCount)
                .build();
    }
}
