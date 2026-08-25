package com.roomsync.admin.service;

import com.roomsync.admin.dto.CurrentBookingSummary;
import com.roomsync.admin.dto.OccupancyStatus;
import com.roomsync.admin.dto.RoomOccupancyResponse;
import com.roomsync.booking.entity.Booking;
import com.roomsync.booking.repository.BookingRepository;
import com.roomsync.common.response.PageResponse;
import com.roomsync.common.time.DateTimeProvider;
import com.roomsync.room.entity.Room;
import com.roomsync.room.entity.RoomStatus;
import com.roomsync.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OccupancyService {

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final DateTimeProvider dateTimeProvider;

    @Transactional(readOnly = true)
    public PageResponse<RoomOccupancyResponse> getOccupancy(
            Long locationId,
            Long roomId,
            Pageable pageable) {

        OffsetDateTime now = dateTimeProvider.nowOffsetDateTime();

        Page<Room> roomPage;
        if (roomId != null) {
            Optional<Room> roomOpt = roomRepository.findById(roomId);
            List<Room> rooms = roomOpt.map(List::of).orElse(List.of());
            roomPage = new PageImpl<>(rooms, pageable, rooms.size());
        } else if (locationId != null) {
            roomPage = roomRepository.findAllByLocationId(locationId, pageable);
        } else {
            roomPage = roomRepository.findAll(pageable);
        }

        // Batch load active confirmed bookings across all rooms at 'now'
        List<Booking> activeBookings = bookingRepository.findAllActiveConfirmedBookingsAt(now);
        Map<Long, Booking> activeBookingByRoomId = activeBookings.stream()
                .collect(Collectors.toMap(b -> b.getRoom().getId(), b -> b, (b1, b2) -> b1));

        List<RoomOccupancyResponse> responses = new ArrayList<>();
        for (Room room : roomPage.getContent()) {
            OccupancyStatus occupancyStatus;
            CurrentBookingSummary currentBooking = null;

            if (room.getStatus() == RoomStatus.LOCKED) {
                occupancyStatus = OccupancyStatus.LOCKED;
            } else if (room.getStatus() == RoomStatus.AVAILABLE) {
                Booking booking = activeBookingByRoomId.get(room.getId());
                if (booking != null) {
                    occupancyStatus = OccupancyStatus.OCCUPIED;
                    currentBooking = CurrentBookingSummary.fromEntity(booking);
                } else {
                    occupancyStatus = OccupancyStatus.AVAILABLE;
                }
            } else {
                occupancyStatus = OccupancyStatus.AVAILABLE;
            }

            responses.add(RoomOccupancyResponse.builder()
                    .roomId(room.getId())
                    .roomName(room.getName())
                    .capacity(room.getCapacity())
                    .locationId(room.getLocation() != null ? room.getLocation().getId() : null)
                    .locationName(room.getLocation() != null ? room.getLocation().getName() : null)
                    .locationTimezone(room.getLocation() != null ? room.getLocation().getTimezone() : null)
                    .administrativeState(room.getStatus())
                    .active(room.isActive())
                    .occupancyStatus(occupancyStatus)
                    .currentBooking(currentBooking)
                    .build());
        }

        return PageResponse.fromPage(roomPage, r -> responses.get(roomPage.getContent().indexOf(r)));
    }
}
