package com.roomsync.notification.service;

import com.roomsync.booking.entity.Booking;
import com.roomsync.common.time.DateTimeProvider;
import com.roomsync.location.entity.Location;
import com.roomsync.notification.entity.NotificationOutbox;
import com.roomsync.notification.entity.NotificationStatus;
import com.roomsync.notification.repository.NotificationOutboxRepository;
import com.roomsync.room.entity.Room;
import com.roomsync.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxServiceTest {

    @Mock
    private NotificationOutboxRepository notificationOutboxRepository;

    @Mock
    private DateTimeProvider dateTimeProvider;

    @InjectMocks
    private NotificationOutboxService notificationOutboxService;

    private User user;
    private Location location;
    private Room room;
    private Booking booking;

    @BeforeEach
    void setUp() {
        user = User.builder().id(10L).name("Alice").email("alice@roomsync.com").build();
        location = Location.builder().id(1L).name("Mumbai").timezone("Asia/Kolkata").build();
        room = Room.builder().id(20L).name("Room Alpha").location(location).build();
        booking = Booking.builder()
                .id(100L)
                .user(user)
                .room(room)
                .startTime(OffsetDateTime.parse("2026-09-01T10:00:00Z"))
                .endTime(OffsetDateTime.parse("2026-09-01T11:00:00Z"))
                .reason("Design Review")
                .build();
    }

    @Test
    @DisplayName("Create booking outbox event sets PENDING status, recipient, payload, and unique eventId")
    void testCreateBookingEvent() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-25T00:00:00Z");
        when(dateTimeProvider.nowOffsetDateTime()).thenReturn(now);
        when(notificationOutboxRepository.save(any(NotificationOutbox.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationOutbox event = notificationOutboxService.createBookingEvent("BOOKING_CONFIRMED", booking, Map.of("customKey", "customValue"));

        assertThat(event.getEventId()).startsWith("evt_booking_100_booking_confirmed_");
        assertThat(event.getEventType()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(event.getAggregateType()).isEqualTo("BOOKING");
        assertThat(event.getAggregateId()).isEqualTo("100");
        assertThat(event.getRecipient()).isEqualTo("alice@roomsync.com");
        assertThat(event.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(event.getAttemptCount()).isEqualTo(0);
        assertThat(event.getNextAttemptTime()).isEqualTo(now);
        assertThat(event.getPayload()).containsEntry("bookingId", 100L);
        assertThat(event.getPayload()).containsEntry("roomName", "Room Alpha");
        assertThat(event.getPayload()).containsEntry("customKey", "customValue");
    }
}
