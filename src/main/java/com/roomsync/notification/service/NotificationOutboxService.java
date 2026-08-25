package com.roomsync.notification.service;

import com.roomsync.booking.entity.Booking;
import com.roomsync.common.time.DateTimeProvider;
import com.roomsync.notification.entity.NotificationOutbox;
import com.roomsync.notification.entity.NotificationStatus;
import com.roomsync.notification.repository.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationOutboxService {

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final DateTimeProvider dateTimeProvider;

    @Transactional
    public NotificationOutbox createBookingEvent(
            String eventType,
            Booking booking,
            Map<String, Object> additionalPayload) {

        String eventId = "evt_booking_" + booking.getId() + "_" + eventType.toLowerCase() + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        Map<String, Object> payload = new HashMap<>();
        payload.put("bookingId", booking.getId());
        payload.put("roomId", booking.getRoom() != null ? booking.getRoom().getId() : null);
        payload.put("roomName", booking.getRoom() != null ? booking.getRoom().getName() : null);
        payload.put("locationId", booking.getRoom() != null && booking.getRoom().getLocation() != null ? booking.getRoom().getLocation().getId() : null);
        payload.put("locationName", booking.getRoom() != null && booking.getRoom().getLocation() != null ? booking.getRoom().getLocation().getName() : null);
        payload.put("startTime", booking.getStartTime() != null ? booking.getStartTime().toString() : null);
        payload.put("endTime", booking.getEndTime() != null ? booking.getEndTime().toString() : null);
        payload.put("reason", booking.getReason());
        payload.put("userId", booking.getUser() != null ? booking.getUser().getId() : null);
        payload.put("userEmail", booking.getUser() != null ? booking.getUser().getEmail() : null);
        payload.put("userName", booking.getUser() != null ? booking.getUser().getName() : null);
        if (booking.getSeries() != null) {
            payload.put("seriesId", booking.getSeries().getId());
            payload.put("occurrenceIndex", booking.getOccurrenceIndex());
        }
        if (booking.getRescheduledFrom() != null) {
            payload.put("rescheduledFromId", booking.getRescheduledFrom().getId());
        }
        if (additionalPayload != null) {
            payload.putAll(additionalPayload);
        }

        OffsetDateTime now = dateTimeProvider.nowOffsetDateTime();

        NotificationOutbox outbox = NotificationOutbox.builder()
                .eventId(eventId)
                .eventType(eventType)
                .aggregateType("BOOKING")
                .aggregateId(booking.getId().toString())
                .recipient(booking.getUser() != null ? booking.getUser().getEmail() : "user@roomsync.com")
                .payload(payload)
                .status(NotificationStatus.PENDING)
                .attemptCount(0)
                .nextAttemptTime(now)
                .build();

        NotificationOutbox saved = notificationOutboxRepository.save(outbox);
        log.info("Created NotificationOutbox id: {} eventId: {} eventType: {} for booking: {}",
                saved.getId(), saved.getEventId(), eventType, booking.getId());
        return saved;
    }
}
