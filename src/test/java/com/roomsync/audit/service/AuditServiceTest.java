package com.roomsync.audit.service;

import com.roomsync.audit.entity.AuditLog;
import com.roomsync.audit.repository.AuditLogRepository;
import com.roomsync.booking.entity.Booking;
import com.roomsync.location.entity.Location;
import com.roomsync.room.entity.Room;
import com.roomsync.user.entity.User;
import com.roomsync.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuditService auditService;

    private User user;
    private Location location;
    private Room room;
    private Booking booking;

    @BeforeEach
    void setUp() {
        user = User.builder().id(10L).name("Alice").email("alice@roomsync.com").build();
        location = Location.builder().id(1L).name("Mumbai").timezone("Asia/Kolkata").build();
        room = Room.builder().id(20L).name("Room Alpha").location(location).build();
        booking = Booking.builder().id(100L).user(user).room(room).build();
    }

    @Test
    @DisplayName("Booking CREATE audit log records authenticated user and booking details")
    void testLogBookingCreate() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditLog log = auditService.logBookingAction("BOOKING_CREATED", booking, 10L, Map.of("reason", "Team Sync"));

        assertThat(log.getAction()).isEqualTo("BOOKING_CREATED");
        assertThat(log.getActorUser()).isEqualTo(user);
        assertThat(log.getAffectedUser()).isEqualTo(user);
        assertThat(log.getBooking()).isEqualTo(booking);
        assertThat(log.getRoom()).isEqualTo(room);
        assertThat(log.getLocation()).isEqualTo(location);
        assertThat(log.getMetadata()).containsEntry("reason", "Team Sync");
        assertThat(log.getMetadata()).containsEntry("actorType", "USER");
    }

    @Test
    @DisplayName("Booking CANCEL audit log records actor and cancellation reason")
    void testLogBookingCancel() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditLog log = auditService.logBookingAction("BOOKING_CANCELLED", booking, 10L, Map.of("cancelledReason", "Meeting moved"));

        assertThat(log.getAction()).isEqualTo("BOOKING_CANCELLED");
        assertThat(log.getMetadata()).containsEntry("cancelledReason", "Meeting moved");
    }

    @Test
    @DisplayName("Booking RESCHEDULE audit log records lineage and details")
    void testLogBookingReschedule() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditLog log = auditService.logBookingAction("BOOKING_RESCHEDULED", booking, 10L, Map.of("originalBookingId", 99L));

        assertThat(log.getAction()).isEqualTo("BOOKING_RESCHEDULED");
        assertThat(log.getMetadata()).containsEntry("originalBookingId", 99L);
    }

    @Test
    @DisplayName("Booking COMPLETION audit log supports SYSTEM actor when actorUserId is null")
    void testLogBookingCompletionSystemActor() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditLog log = auditService.logBookingAction("BOOKING_COMPLETED", booking, null, Map.of("completedAt", "2026-08-25T10:00:00Z"));

        assertThat(log.getAction()).isEqualTo("BOOKING_COMPLETED");
        assertThat(log.getActorUser()).isNull();
        assertThat(log.getMetadata()).containsEntry("actorType", "SYSTEM");
    }
}
