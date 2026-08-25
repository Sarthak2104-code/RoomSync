package com.roomsync.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomsync.audit.entity.AuditLog;
import com.roomsync.audit.repository.AuditLogRepository;
import com.roomsync.booking.dto.CreateBookingRequest;
import com.roomsync.booking.dto.RescheduleBookingRequest;
import com.roomsync.booking.entity.Booking;
import com.roomsync.booking.entity.BookingStatus;
import com.roomsync.booking.repository.BookingRepository;
import com.roomsync.booking.service.BookingService;
import com.roomsync.location.entity.Location;
import com.roomsync.location.repository.LocationRepository;
import com.roomsync.notification.entity.NotificationOutbox;
import com.roomsync.notification.entity.NotificationStatus;
import com.roomsync.notification.repository.NotificationOutboxRepository;
import com.roomsync.notification.service.EmailService;
import com.roomsync.notification.worker.NotificationWorker;
import com.roomsync.room.entity.Room;
import com.roomsync.room.entity.RoomStatus;
import com.roomsync.room.repository.RoomRepository;
import com.roomsync.security.jwt.JwtTokenProvider;
import com.roomsync.user.entity.Role;
import com.roomsync.user.entity.User;
import com.roomsync.user.repository.RoleRepository;
import com.roomsync.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuditOutboxIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private NotificationWorker notificationWorker;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private EmailService emailService;

    private User testUser;
    private Location testLocation;
    private Room testRoom;
    private Room testRoom2;
    private String userToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE notification_outbox, audit_logs, bookings, booking_series, rooms, users, locations RESTART IDENTITY CASCADE");

        testLocation = locationRepository.save(Location.builder()
                .name("Mumbai HQ")
                .code("MUM")
                .timezone("Asia/Kolkata")
                .active(true)
                .build());

        Role userRole = roleRepository.findByName("USER").orElseGet(() ->
                roleRepository.save(Role.builder().name("USER").build()));

        testUser = userRepository.save(User.builder()
                .name("Alice")
                .email("alice@roomsync.com")
                .password("hashPass")
                .role(userRole)
                .location(testLocation)
                .active(true)
                .build());

        testRoom = roomRepository.save(Room.builder()
                .name("Room Alpha")
                .capacity(10)
                .location(testLocation)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build());

        testRoom2 = roomRepository.save(Room.builder()
                .name("Room Beta")
                .capacity(15)
                .location(testLocation)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build());

        userToken = jwtTokenProvider.generateAccessToken(testUser.getId(), testUser.getEmail(), "USER", testLocation.getId());
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("TRUNCATE notification_outbox, audit_logs, bookings, booking_series, rooms, users, locations RESTART IDENTITY CASCADE");
    }

    @Test
    @DisplayName("D & E. Booking Creation Atomicity: Booking + Audit + Outbox persisted atomically; Email failure isolated from booking success")
    void testBookingCreationAtomicityAndEmailIsolation() throws Exception {
        // Configure EmailService to throw exception simulating provider outage
        doThrow(new RuntimeException("Simulated Email Provider 503 Outage")).when(emailService).sendEmail(anyString(), anyString(), anyString());

        CreateBookingRequest request = CreateBookingRequest.builder()
                .roomId(testRoom.getId())
                .startTime(OffsetDateTime.parse("2026-09-01T04:30:00Z")) // 10:00 IST
                .endTime(OffsetDateTime.parse("2026-09-01T05:30:00Z"))   // 11:00 IST
                .reason("Critical Architecture Review")
                .build();

        // 1. POST booking -> 201 CREATED (Must succeed even if email provider is dead!)
        String resp = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andReturn().getResponse().getContentAsString();

        Long bookingId = objectMapper.readTree(resp).get("id").asLong();

        // 2. Verify Database State
        // Booking exists and is CONFIRMED
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        // Audit Log exists
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).hasSize(1);
        AuditLog audit = auditLogs.get(0);
        assertThat(audit.getAction()).isEqualTo("BOOKING_CREATED");
        assertThat(audit.getActorUser().getId()).isEqualTo(testUser.getId());
        assertThat(audit.getBooking().getId()).isEqualTo(bookingId);

        // Notification Outbox exists with PENDING
        List<NotificationOutbox> outboxList = notificationOutboxRepository.findAll();
        assertThat(outboxList).hasSize(1);
        NotificationOutbox outbox = outboxList.get(0);
        assertThat(outbox.getEventType()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(outbox.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(outbox.getRecipient()).isEqualTo("alice@roomsync.com");

        // 3. Execute Worker while email provider is down
        notificationWorker.processPendingNotifications();

        // Outbox event transitions to FAILED with error recorded, but booking remains untouched!
        NotificationOutbox failedOutbox = notificationOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(failedOutbox.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(failedOutbox.getAttemptCount()).isEqualTo(1);
        assertThat(failedOutbox.getLastError()).contains("Simulated Email Provider 503 Outage");
        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        // 4. Email provider recovers -> worker retries and marks SENT
        doNothing().when(emailService).sendEmail(anyString(), anyString(), anyString());
        // Set nextAttemptTime to now to simulate retry window
        failedOutbox.setNextAttemptTime(OffsetDateTime.now().minusSeconds(10));
        notificationOutboxRepository.saveAndFlush(failedOutbox);

        notificationWorker.processPendingNotifications();

        NotificationOutbox sentOutbox = notificationOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(sentOutbox.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(sentOutbox.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("F. Reschedule Booking Atomicity: Original cancelled, replacement confirmed, audit & outbox records generated")
    void testRescheduleAtomicity() throws Exception {
        doNothing().when(emailService).sendEmail(anyString(), anyString(), anyString());

        // Create initial booking
        Booking original = bookingRepository.save(Booking.builder()
                .room(testRoom)
                .user(testUser)
                .startTime(OffsetDateTime.parse("2026-09-02T04:30:00Z"))
                .endTime(OffsetDateTime.parse("2026-09-02T05:30:00Z"))
                .reason("Initial Discussion")
                .status(BookingStatus.CONFIRMED)
                .build());

        RescheduleBookingRequest rescheduleReq = RescheduleBookingRequest.builder()
                .roomId(testRoom2.getId())
                .startTime(OffsetDateTime.parse("2026-09-02T06:30:00Z"))
                .endTime(OffsetDateTime.parse("2026-09-02T07:30:00Z"))
                .build();

        mockMvc.perform(put("/api/bookings/" + original.getId())
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rescheduleReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Verify original is CANCELLED with reason RESCHEDULED
        Booking originalDb = bookingRepository.findById(original.getId()).orElseThrow();
        assertThat(originalDb.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(originalDb.getCancelledReason()).isEqualTo("RESCHEDULED");

        // Verify audit logs: BOOKING_CANCELLED (for original) and BOOKING_RESCHEDULED (for replacement)
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).extracting(AuditLog::getAction)
                .contains("BOOKING_CANCELLED", "BOOKING_RESCHEDULED");

        // Verify outbox contains BOOKING_RESCHEDULED
        List<NotificationOutbox> outboxList = notificationOutboxRepository.findAll();
        assertThat(outboxList).extracting(NotificationOutbox::getEventType)
                .contains("BOOKING_RESCHEDULED");
    }

    @Test
    @DisplayName("Cancellation Atomicity: Cancelling booking creates audit and outbox event")
    void testCancelBookingAtomicity() throws Exception {
        Booking booking = bookingRepository.save(Booking.builder()
                .room(testRoom)
                .user(testUser)
                .startTime(OffsetDateTime.parse("2026-09-03T04:30:00Z"))
                .endTime(OffsetDateTime.parse("2026-09-03T05:30:00Z"))
                .reason("To be cancelled")
                .status(BookingStatus.CONFIRMED)
                .build());

        mockMvc.perform(delete("/api/bookings/" + booking.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());

        Booking cancelled = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).hasSize(1);
        assertThat(auditLogs.get(0).getAction()).isEqualTo("BOOKING_CANCELLED");

        List<NotificationOutbox> outboxList = notificationOutboxRepository.findAll();
        assertThat(outboxList).hasSize(1);
        assertThat(outboxList.get(0).getEventType()).isEqualTo("BOOKING_CANCELLED");
    }

    @Test
    @DisplayName("G. Booking Completion: Expired bookings create audit and outbox records with SYSTEM actor")
    void testCompletionSchedulerAtomicity() {
        // Create past confirmed booking
        Booking pastBooking = bookingRepository.save(Booking.builder()
                .room(testRoom)
                .user(testUser)
                .startTime(OffsetDateTime.parse("2026-08-20T04:30:00Z"))
                .endTime(OffsetDateTime.parse("2026-08-20T05:30:00Z"))
                .reason("Past event")
                .status(BookingStatus.CONFIRMED)
                .build());

        int completed = bookingService.completePastBookings();
        assertThat(completed).isEqualTo(1);

        Booking updated = bookingRepository.findById(pastBooking.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(BookingStatus.COMPLETED);

        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).hasSize(1);
        assertThat(auditLogs.get(0).getAction()).isEqualTo("BOOKING_COMPLETED");
        assertThat(auditLogs.get(0).getActorUser()).isNull(); // SYSTEM actor
        assertThat(auditLogs.get(0).getMetadata()).containsEntry("actorType", "SYSTEM");

        List<NotificationOutbox> outboxList = notificationOutboxRepository.findAll();
        assertThat(outboxList).hasSize(1);
        assertThat(outboxList.get(0).getEventType()).isEqualTo("BOOKING_COMPLETED");
    }
}
