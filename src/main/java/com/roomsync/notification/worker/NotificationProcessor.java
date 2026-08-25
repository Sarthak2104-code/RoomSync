package com.roomsync.notification.worker;

import com.roomsync.common.time.DateTimeProvider;
import com.roomsync.notification.entity.NotificationOutbox;
import com.roomsync.notification.entity.NotificationStatus;
import com.roomsync.notification.repository.NotificationOutboxRepository;
import com.roomsync.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationProcessor {

    private static final int MAX_ATTEMPTS = 5;
    private static final int[] BACKOFF_SECONDS = {60, 300, 900, 1800, 3600};

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final EmailService emailService;
    private final DateTimeProvider dateTimeProvider;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processSingleEvent(Long outboxId) {
        NotificationOutbox outbox = notificationOutboxRepository.findById(outboxId).orElse(null);
        if (outbox == null) {
            return false;
        }

        // Idempotency check: Already SENT events are never reprocessed
        if (outbox.getStatus() == NotificationStatus.SENT) {
            log.info("Notification event id: {} already SENT, skipping", outbox.getId());
            return true;
        }

        // Transition to PROCESSING
        outbox.setStatus(NotificationStatus.PROCESSING);
        outbox.setAttemptCount(outbox.getAttemptCount() + 1);
        notificationOutboxRepository.saveAndFlush(outbox);

        try {
            String subject = String.format("RoomSync Notification: %s", outbox.getEventType());
            String body = String.format("Notification for %s (%s): %s",
                    outbox.getAggregateType(), outbox.getAggregateId(), outbox.getPayload());

            emailService.sendEmail(outbox.getRecipient(), subject, body);

            // Successfully sent
            outbox.setStatus(NotificationStatus.SENT);
            outbox.setProcessedAt(dateTimeProvider.nowOffsetDateTime());
            outbox.setLastError(null);
            notificationOutboxRepository.saveAndFlush(outbox);
            log.info("Successfully delivered notification outbox id: {} eventId: {}", outbox.getId(), outbox.getEventId());
            return true;

        } catch (Exception ex) {
            log.warn("Failed to deliver notification outbox id: {} eventId: {} (attempt {}): {}",
                    outbox.getId(), outbox.getEventId(), outbox.getAttemptCount(), ex.getMessage());

            outbox.setLastError(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());

            if (outbox.getAttemptCount() >= MAX_ATTEMPTS) {
                outbox.setStatus(NotificationStatus.DEAD_LETTER);
                outbox.setNextAttemptTime(null);
            } else {
                outbox.setStatus(NotificationStatus.FAILED);
                int backoff = BACKOFF_SECONDS[Math.min(outbox.getAttemptCount() - 1, BACKOFF_SECONDS.length - 1)];
                outbox.setNextAttemptTime(dateTimeProvider.nowOffsetDateTime().plusSeconds(backoff));
            }

            notificationOutboxRepository.saveAndFlush(outbox);
            return false;
        }
    }
}
