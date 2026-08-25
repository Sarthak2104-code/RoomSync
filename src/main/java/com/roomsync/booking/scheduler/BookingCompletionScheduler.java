package com.roomsync.booking.scheduler;

import com.roomsync.booking.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic background scheduler that transitions CONFIRMED bookings whose end time has passed to COMPLETED.
 * Safe to execute repeatedly (idempotent). Does not mutate CANCELLED or already COMPLETED bookings.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "roomsync.scheduler.booking-completion.enabled", havingValue = "true", matchIfMissing = true)
public class BookingCompletionScheduler {

    private final BookingService bookingService;

    @Scheduled(fixedRateString = "${roomsync.scheduler.booking-completion-rate-ms:60000}")
    public void runBookingCompletion() {
        try {
            int count = bookingService.completePastBookings();
            if (count > 0) {
                log.debug("Booking completion scheduled job marked {} bookings as COMPLETED", count);
            }
        } catch (Exception ex) {
            log.error("Error occurred during scheduled booking completion run", ex);
        }
    }
}
