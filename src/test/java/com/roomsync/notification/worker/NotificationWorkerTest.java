package com.roomsync.notification.worker;

import com.roomsync.common.time.DateTimeProvider;
import com.roomsync.notification.entity.NotificationOutbox;
import com.roomsync.notification.entity.NotificationStatus;
import com.roomsync.notification.repository.NotificationOutboxRepository;
import com.roomsync.notification.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationWorkerTest {

    @Mock
    private NotificationOutboxRepository notificationOutboxRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private DateTimeProvider dateTimeProvider;

    @InjectMocks
    private NotificationProcessor notificationProcessor;

    private NotificationWorker notificationWorker;

    private NotificationOutbox pendingOutbox;
    private OffsetDateTime fixedNow;

    @BeforeEach
    void setUp() {
        fixedNow = OffsetDateTime.parse("2026-08-25T10:00:00Z");
        pendingOutbox = NotificationOutbox.builder()
                .id(1L)
                .eventId("evt_123")
                .eventType("BOOKING_CONFIRMED")
                .aggregateType("BOOKING")
                .aggregateId("100")
                .recipient("alice@roomsync.com")
                .payload(Map.of("bookingId", 100L))
                .status(NotificationStatus.PENDING)
                .attemptCount(0)
                .build();

        notificationWorker = new NotificationWorker(notificationOutboxRepository, notificationProcessor, dateTimeProvider);
    }

    @Test
    @DisplayName("Successful email delivery marks event SENT and records processedAt")
    void testProcessSingleEventSuccess() {
        when(notificationOutboxRepository.findById(1L)).thenReturn(Optional.of(pendingOutbox));
        when(dateTimeProvider.nowOffsetDateTime()).thenReturn(fixedNow);
        when(notificationOutboxRepository.saveAndFlush(any(NotificationOutbox.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean result = notificationProcessor.processSingleEvent(1L);

        assertThat(result).isTrue();
        assertThat(pendingOutbox.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(pendingOutbox.getProcessedAt()).isEqualTo(fixedNow);
        assertThat(pendingOutbox.getLastError()).isNull();
        verify(emailService).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Email provider failure marks event FAILED, increments attemptCount, sets backoff and lastError")
    void testProcessSingleEventFailure() {
        when(notificationOutboxRepository.findById(1L)).thenReturn(Optional.of(pendingOutbox));
        when(dateTimeProvider.nowOffsetDateTime()).thenReturn(fixedNow);
        doThrow(new RuntimeException("SMTP Connection Timeout")).when(emailService).sendEmail(anyString(), anyString(), anyString());
        when(notificationOutboxRepository.saveAndFlush(any(NotificationOutbox.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean result = notificationProcessor.processSingleEvent(1L);

        assertThat(result).isFalse();
        assertThat(pendingOutbox.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(pendingOutbox.getAttemptCount()).isEqualTo(1);
        assertThat(pendingOutbox.getLastError()).isEqualTo("SMTP Connection Timeout");
        assertThat(pendingOutbox.getNextAttemptTime()).isEqualTo(fixedNow.plusSeconds(60));
    }

    @Test
    @DisplayName("Idempotency: Already SENT event is skipped and not reprocessed")
    void testAlreadySentEventSkipped() {
        pendingOutbox.setStatus(NotificationStatus.SENT);
        when(notificationOutboxRepository.findById(1L)).thenReturn(Optional.of(pendingOutbox));

        boolean result = notificationProcessor.processSingleEvent(1L);

        assertThat(result).isTrue();
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Worker claims pending events and delegates to processor")
    void testWorkerBatchProcessing() {
        when(dateTimeProvider.nowOffsetDateTime()).thenReturn(fixedNow);
        when(notificationOutboxRepository.claimPendingNotificationIds(fixedNow, 50)).thenReturn(List.of(1L));
        when(notificationOutboxRepository.findById(1L)).thenReturn(Optional.of(pendingOutbox));
        when(notificationOutboxRepository.saveAndFlush(any(NotificationOutbox.class))).thenAnswer(inv -> inv.getArgument(0));

        int successCount = notificationWorker.processPendingNotifications();

        assertThat(successCount).isEqualTo(1);
        verify(emailService).sendEmail(anyString(), anyString(), anyString());
    }
}
