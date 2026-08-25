package com.roomsync.admin.service;

import com.roomsync.booking.dto.BookingResponse;
import com.roomsync.booking.entity.Booking;
import com.roomsync.booking.entity.BookingStatus;
import com.roomsync.booking.exception.BookingNotFoundException;
import com.roomsync.booking.repository.BookingRepository;
import com.roomsync.common.response.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final BookingRepository bookingRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> getAdminBookings(
            Long locationId,
            Long roomId,
            Long userId,
            BookingStatus status,
            Pageable pageable) {

        Page<Booking> page = bookingRepository.findAllAdminBookings(locationId, roomId, userId, status, pageable);
        return PageResponse.fromPage(page, b -> BookingResponse.fromEntity(b, clock));
    }

    @Transactional(readOnly = true)
    public BookingResponse getAdminBookingById(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException(id));
        return BookingResponse.fromEntity(booking, clock);
    }
}
