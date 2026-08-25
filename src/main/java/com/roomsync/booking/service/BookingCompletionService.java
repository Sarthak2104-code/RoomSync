package com.roomsync.booking.service;

import com.roomsync.audit.service.AuditService;
import com.roomsync.booking.entity.Booking;
import com.roomsync.booking.entity.BookingStatus;
import com.roomsync.booking.repository.BookingRepository;
import com.roomsync.common.time.DateTimeProvider;
import com.roomsync.notification.service.NotificationOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingCompletionService {

    private final BookingRepository bookingRepository;
    private final AuditService auditService;
    private final NotificationOutboxService notificationOutboxService;
    private final DateTimeProvider dateTimeProvider;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean completeSingleBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null) {
            return false;
        }

        // Idempotency check: Only CONFIRMED bookings can be completed
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            return false;
        }

        OffsetDateTime now = dateTimeProvider.nowOffsetDateTime();
        if (!booking.getEndTime().isBefore(now)) {
            return false;
        }

        booking.setStatus(BookingStatus.COMPLETED);
        booking.setUpdatedAt(now);
        Booking saved = bookingRepository.saveAndFlush(booking);

        // Audit + Outbox in the same transaction
        auditService.logBookingAction("BOOKING_COMPLETED", saved, null, Map.of("actorType", "SYSTEM", "completedAt", now.toString()));
        notificationOutboxService.createBookingEvent("BOOKING_COMPLETED", saved, Map.of("trigger", "AUTO_COMPLETION"));

        log.info("Auto-completed booking id: {} with audit and outbox event", saved.getId());
        return true;
    }
}
