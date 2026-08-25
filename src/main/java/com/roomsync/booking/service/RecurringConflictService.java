package com.roomsync.booking.service;

import com.roomsync.booking.dto.RecurringOccurrencePreview;
import com.roomsync.booking.entity.RecurrenceFrequency;
import com.roomsync.booking.exception.InvalidBookingTimeException;
import com.roomsync.booking.repository.BookingRepository;
import com.roomsync.common.time.BookingInterval;
import com.roomsync.common.time.TimeService;
import com.roomsync.common.time.TimezoneService;
import com.roomsync.room.entity.Room;
import com.roomsync.room.entity.RoomStatus;
import com.roomsync.room.exception.RoomNotFoundException;
import com.roomsync.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates generated occurrences for a target room to identify availability and conflicts without creating bookings.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringConflictService {

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final TimezoneService timezoneService;
    private final TimeService timeService;

    @Transactional(readOnly = true)
    public List<RecurringOccurrencePreview> evaluateOccurrences(
            Long roomId,
            LocalTime startLocalTime,
            LocalTime endLocalTime,
            List<RecurrenceGenerator.OccurrenceDate> occurrenceDates) {

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));

        ZoneId zoneId = timezoneService.getLocationZoneId(room.getLocation());

        List<RecurringOccurrencePreview> previews = new ArrayList<>();

        for (RecurrenceGenerator.OccurrenceDate occ : occurrenceDates) {
            RecurringOccurrencePreview preview = evaluateSingleOccurrence(room, zoneId, occ.getOccurrenceIndex(), occ.getDate(), startLocalTime, endLocalTime);
            previews.add(preview);
        }

        return previews;
    }

    public RecurringOccurrencePreview evaluateSingleOccurrence(
            Room room,
            ZoneId zoneId,
            int occurrenceIndex,
            LocalDate date,
            LocalTime startLocalTime,
            LocalTime endLocalTime) {

        if (!room.getLocation().isActive()) {
            return RecurringOccurrencePreview.builder()
                    .occurrenceIndex(occurrenceIndex)
                    .date(date)
                    .availability("CONFLICT")
                    .conflictReason(String.format("Location '%s' is inactive", room.getLocation().getName()))
                    .build();
        }

        if (!room.isActive()) {
            return RecurringOccurrencePreview.builder()
                    .occurrenceIndex(occurrenceIndex)
                    .date(date)
                    .availability("CONFLICT")
                    .conflictReason(String.format("Room '%s' is inactive", room.getName()))
                    .build();
        }

        if (room.getStatus() == RoomStatus.LOCKED) {
            return RecurringOccurrencePreview.builder()
                    .occurrenceIndex(occurrenceIndex)
                    .date(date)
                    .availability("CONFLICT")
                    .conflictReason(String.format("Room '%s' is currently locked", room.getName()))
                    .build();
        }

        try {
            BookingInterval interval = timeService.calculateBookingInterval(date, startLocalTime, endLocalTime, zoneId);

            boolean hasOverlap = bookingRepository.existsOverlappingBooking(
                    room.getId(),
                    interval.startUtc(),
                    interval.endUtc()
            );

            if (hasOverlap) {
                return RecurringOccurrencePreview.builder()
                    .occurrenceIndex(occurrenceIndex)
                    .date(date)
                    .startTime(interval.startUtc())
                    .endTime(interval.endUtc())
                    .availability("CONFLICT")
                    .conflictReason("The requested time slot overlaps with an existing confirmed booking")
                    .build();
            }

            return RecurringOccurrencePreview.builder()
                    .occurrenceIndex(occurrenceIndex)
                    .date(date)
                    .startTime(interval.startUtc())
                    .endTime(interval.endUtc())
                    .availability("AVAILABLE")
                    .conflictReason(null)
                    .build();

        } catch (InvalidBookingTimeException ex) {
            return RecurringOccurrencePreview.builder()
                    .occurrenceIndex(occurrenceIndex)
                    .date(date)
                    .availability("CONFLICT")
                    .conflictReason(ex.getMessage())
                    .build();
        } catch (Exception ex) {
            log.warn("Unexpected evaluation error for occurrence {}: {}", occurrenceIndex, ex.getMessage());
            return RecurringOccurrencePreview.builder()
                    .occurrenceIndex(occurrenceIndex)
                    .date(date)
                    .availability("CONFLICT")
                    .conflictReason(ex.getMessage())
                    .build();
        }
    }
}
