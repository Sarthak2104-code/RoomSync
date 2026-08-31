package com.roomsync.audit.service;

import com.roomsync.audit.dto.AuditLogResponse;
import com.roomsync.audit.entity.AuditLog;
import com.roomsync.audit.repository.AuditLogRepository;
import com.roomsync.booking.entity.Booking;
import com.roomsync.common.response.PageResponse;
import com.roomsync.location.entity.Location;
import com.roomsync.room.entity.Room;
import com.roomsync.user.entity.User;
import com.roomsync.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    private User adminUser;
    private User normalUser;
    private Location location;
    private Room room;
    private Booking booking;

    @BeforeEach
    void setUp() {
        location = Location.builder().id(1L).name("Bengaluru").code("BLG").build();
        room = Room.builder().id(10L).name("Cauvery").location(location).build();
        adminUser = User.builder().id(1L).wissenId("WT1181").name("Admin").email("admin@roomsync.com").location(location).build();
        normalUser = User.builder().id(2L).wissenId("WT1182").name("User").email("user@roomsync.com").location(location).build();
        booking = Booking.builder().id(100L).room(room).user(normalUser).build();
    }

    @Test
    @DisplayName("Should log booking action with correct actor and affected user")
    void shouldLogBookingAction() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> {
            AuditLog al = i.getArgument(0);
            al.setId(500L);
            return al;
        });

        AuditLog result = auditService.logBookingAction("BOOKING_CANCELLED", booking, 1L, Map.of("reason", "Maintenance"));

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(500L);
        assertThat(result.getAction()).isEqualTo("BOOKING_CANCELLED");
        assertThat(result.getActorUser()).isEqualTo(adminUser);
        assertThat(result.getAffectedUser()).isEqualTo(normalUser);
        assertThat(result.getBooking()).isEqualTo(booking);
        assertThat(result.getRoom()).isEqualTo(room);
        assertThat(result.getLocation()).isEqualTo(location);
    }

    @Test
    @DisplayName("Should query audit logs with filters and pagination")
    void shouldQueryAuditLogsWithFilters() {
        AuditLog al = AuditLog.builder()
                .id(1L)
                .actorUser(adminUser)
                .affectedUser(normalUser)
                .action("BOOKING_CREATED")
                .entityType("BOOKING")
                .entityId("100")
                .location(location)
                .room(room)
                .booking(booking)
                .createdAt(OffsetDateTime.now())
                .metadata(Map.of("actorType", "USER"))
                .build();

        Page<AuditLog> page = new PageImpl<>(List.of(al), PageRequest.of(0, 10), 1);
        when(auditLogRepository.findAllByFilters(eq(1L), eq("BOOKING_CREATED"), eq("BOOKING"), eq(1L), eq(10L), eq(100L), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<AuditLogResponse> response = auditService.getAuditLogs(
                1L, "BOOKING_CREATED", "BOOKING", 1L, 10L, 100L, null, null, PageRequest.of(0, 10));

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent().get(0).getAction()).isEqualTo("BOOKING_CREATED");
        assertThat(response.getContent().get(0).getActorEmail()).isEqualTo("admin@roomsync.com");
        assertThat(response.getContent().get(0).getAffectedUserEmail()).isEqualTo("user@roomsync.com");
    }
}
