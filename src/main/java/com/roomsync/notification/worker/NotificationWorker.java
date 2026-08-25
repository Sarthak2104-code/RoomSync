package com.roomsync.notification.worker;

import com.roomsync.common.time.DateTimeProvider;
import com.roomsync.notification.repository.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "roomsync.scheduler.notification-worker.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationWorker {

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final NotificationProcessor notificationProcessor;
    private final DateTimeProvider dateTimeProvider;

    @Scheduled(fixedDelayString = "${roomsync.scheduler.notification-worker-rate-ms:5000}")
    public int processPendingNotifications() {
        try {
            OffsetDateTime now = dateTimeProvider.nowOffsetDateTime();
            List<Long> candidateIds = notificationOutboxRepository.claimPendingNotificationIds(now, 50);

            if (candidateIds.isEmpty()) {
                return 0;
            }

            log.debug("NotificationWorker claimed {} pending notification events", candidateIds.size());
            int successCount = 0;

            for (Long id : candidateIds) {
                try {
                    boolean success = notificationProcessor.processSingleEvent(id);
                    if (success) {
                        successCount++;
                    }
                } catch (Exception ex) {
                    log.error("Unexpected error in NotificationWorker while delegating outbox id: {}", id, ex);
                }
            }

            return successCount;

        } catch (Exception ex) {
            log.error("Error occurred during NotificationWorker batch polling", ex);
            return 0;
        }
    }
}
