package com.roomsync.admin.service;

import com.roomsync.admin.dto.RoomUtilizationResponse;
import com.roomsync.admin.dto.UtilizationReportResponse;
import com.roomsync.booking.entity.Booking;
import com.roomsync.booking.repository.BookingRepository;
import com.roomsync.common.time.DateTimeProvider;
import com.roomsync.common.time.TimezoneService;
import com.roomsync.room.entity.Room;
import com.roomsync.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final TimezoneService timezoneService;
    private final DateTimeProvider dateTimeProvider;

    @Transactional(readOnly = true)
    public UtilizationReportResponse getUtilization(
            LocalDate startDate,
            LocalDate endDate,
            Long locationId,
            Long roomId) {

        if (startDate == null) {
            startDate = LocalDate.now(dateTimeProvider.getClock());
        }
        if (endDate == null) {
            endDate = startDate;
        }

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate cannot be before startDate");
        }

        List<Room> targetRooms;
        if (roomId != null) {
            Optional<Room> roomOpt = roomRepository.findById(roomId);
            targetRooms = roomOpt.map(List::of).orElse(List.of());
        } else if (locationId != null) {
            targetRooms = roomRepository.findAllByLocationId(locationId, org.springframework.data.domain.Pageable.unpaged()).getContent();
        } else {
            targetRooms = roomRepository.findAll();
        }

        List<RoomUtilizationResponse> roomResponses = new ArrayList<>();
        long grandTotalAvailableMinutes = 0;
        long grandTotalBookedMinutes = 0;

        for (Room room : targetRooms) {
            ZoneId zoneId = timezoneService.getLocationZoneId(room.getLocation());

            // 1. Convert requested reporting dates into room's authoritative timezone
            // Half-open interval: [startDate 00:00, day after endDate 00:00)
            ZonedDateTime startLocalZoned = startDate.atStartOfDay(zoneId);
            ZonedDateTime endLocalZoned = endDate.plusDays(1).atStartOfDay(zoneId);

            OffsetDateTime startUtc = startLocalZoned.toOffsetDateTime();
            OffsetDateTime endUtc = endLocalZoned.toOffsetDateTime();

            long totalAvailableMinutes = Duration.between(startUtc, endUtc).toMinutes();
            if (totalAvailableMinutes <= 0) {
                totalAvailableMinutes = 1;
            }

            // 2. Retrieve effective bookings overlapping [startUtc, endUtc)
            List<Booking> bookings = bookingRepository.findEffectiveBookingsForRoomInInterval(
                    room.getId(), startUtc, endUtc
            );

            long bookedMinutes = 0;
            for (Booking b : bookings) {
                OffsetDateTime clampedStart = b.getStartTime().isBefore(startUtc) ? startUtc : b.getStartTime();
                OffsetDateTime clampedEnd = b.getEndTime().isAfter(endUtc) ? endUtc : b.getEndTime();

                if (clampedEnd.isAfter(clampedStart)) {
                    bookedMinutes += Duration.between(clampedStart, clampedEnd).toMinutes();
                }
            }

            double utilPercentage = Math.round(((double) bookedMinutes / totalAvailableMinutes) * 10000.0) / 100.0;

            grandTotalAvailableMinutes += totalAvailableMinutes;
            grandTotalBookedMinutes += bookedMinutes;

            roomResponses.add(RoomUtilizationResponse.builder()
                    .roomId(room.getId())
                    .roomName(room.getName())
                    .locationId(room.getLocation() != null ? room.getLocation().getId() : null)
                    .locationName(room.getLocation() != null ? room.getLocation().getName() : null)
                    .locationTimezone(zoneId.getId())
                    .reportingPeriodStart(startUtc)
                    .reportingPeriodEnd(endUtc)
                    .totalAvailableMinutes(totalAvailableMinutes)
                    .totalBookedMinutes(bookedMinutes)
                    .utilizationPercentage(utilPercentage)
                    .bookingCount(bookings.size())
                    .build());
        }

        double overallPercentage = 0.0;
        if (grandTotalAvailableMinutes > 0) {
            overallPercentage = Math.round(((double) grandTotalBookedMinutes / grandTotalAvailableMinutes) * 10000.0) / 100.0;
        }

        return UtilizationReportResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .rooms(roomResponses)
                .overallUtilizationPercentage(overallPercentage)
                .build();
    }
}
