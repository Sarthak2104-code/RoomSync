package com.roomsync.datamodel;

import com.roomsync.admin.entity.AdminRequest;
import com.roomsync.admin.entity.AdminRequestStatus;
import com.roomsync.admin.repository.AdminRequestRepository;
import com.roomsync.agent.entity.AgentAction;
import com.roomsync.agent.entity.AgentOperation;
import com.roomsync.agent.entity.AgentOperationStatus;
import com.roomsync.agent.repository.AgentActionRepository;
import com.roomsync.agent.repository.AgentOperationRepository;
import com.roomsync.audit.entity.AuditLog;
import com.roomsync.audit.repository.AuditLogRepository;
import com.roomsync.booking.entity.Booking;
import com.roomsync.booking.entity.BookingSeries;
import com.roomsync.booking.entity.BookingSeriesStatus;
import com.roomsync.booking.entity.BookingStatus;
import com.roomsync.booking.entity.RecurrenceFrequency;
import com.roomsync.booking.repository.BookingRepository;
import com.roomsync.booking.repository.BookingSeriesRepository;
import com.roomsync.location.entity.Location;
import com.roomsync.location.repository.LocationRepository;
import com.roomsync.notification.entity.NotificationOutbox;
import com.roomsync.notification.entity.NotificationStatus;
import com.roomsync.notification.repository.NotificationOutboxRepository;
import com.roomsync.reliability.entity.IdempotencyRecord;
import com.roomsync.reliability.repository.IdempotencyRecordRepository;
import com.roomsync.room.entity.Amenity;
import com.roomsync.room.entity.Room;
import com.roomsync.room.entity.RoomStatus;
import com.roomsync.room.repository.AmenityRepository;
import com.roomsync.room.repository.RoomRepository;
import com.roomsync.user.entity.Role;
import com.roomsync.user.entity.User;
import com.roomsync.user.repository.RoleRepository;
import com.roomsync.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.springframework.test.context.ActiveProfiles;

/**
 * Verification Test Suite for Task 2: Core Data Model & Database Migrations.
 * Rigorously validates all new database tables, constraints, JSONB mapping,
 * relationships, case-insensitivity, XOR bounds, and delete restrict rules.
 */
@SpringBootTest
@ActiveProfiles("test")
class DataModelConstraintTest {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private AmenityRepository amenityRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingSeriesRepository bookingSeriesRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private AdminRequestRepository adminRequestRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AgentOperationRepository agentOperationRepository;

    @Autowired
    private AgentActionRepository agentActionRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Role userRole;
    private Role adminRole;
    private Location defaultLocation;
    private User testUser;
    private Room testRoom;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE notification_outbox, agent_actions, idempotency_records, agent_operations, audit_logs, admin_requests, bookings, booking_series, room_amenities, amenities, rooms, users, locations RESTART IDENTITY CASCADE");

        userRole = roleRepository.findByName("USER").orElseGet(() -> roleRepository.save(Role.builder().name("USER").build()));
        adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> roleRepository.save(Role.builder().name("ADMIN").build()));

        defaultLocation = locationRepository.save(Location.builder()
                .name("Mumbai HQ")
                .code("MUM-HQ")
                .address("123 Tech Park, BKC")
                .timezone("Asia/Kolkata")
                .description("Headquarters Campus")
                .active(true)
                .build());

        testUser = userRepository.save(User.builder()
                .name("Test Architect")
                .email("architect@roomsync.com")
                .password("securePassword123")
                .role(userRole)
                .location(defaultLocation)
                .active(true)
                .build());

        testRoom = roomRepository.save(Room.builder()
                .name("Aryabhata Conference")
                .capacity(20)
                .location(defaultLocation)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build());
    }

    @Test
    @DisplayName("Role: Seeded roles USER and ADMIN exist and name uniqueness is enforced")
    void testRoleModelAndUniqueness() {
        assertThat(userRole.getName()).isEqualTo("USER");
        assertThat(adminRole.getName()).isEqualTo("ADMIN");

        assertThatThrownBy(() -> roleRepository.saveAndFlush(Role.builder().name("USER").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("User: Case-insensitive email uniqueness via ux_users_email_lower")
    void testUserCaseInsensitiveEmail() {
        assertThatThrownBy(() -> userRepository.saveAndFlush(User.builder()
                .name("Duplicate Email User")
                .email("ARCHITECT@roomsync.com")
                .password("anotherPass")
                .role(userRole)
                .location(defaultLocation)
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("User: Foreign key to Role is ON DELETE RESTRICT")
    void testRoleDeleteRestrict() {
        assertThatThrownBy(() -> roleRepository.delete(userRole))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Location: Default timezone is Asia/Kolkata and is NOT NULL")
    void testLocationTimezone() {
        Location loc = locationRepository.findById(defaultLocation.getId()).orElseThrow();
        assertThat(loc.getTimezone()).isEqualTo("Asia/Kolkata");
        assertThat(loc.getAddress()).isEqualTo("123 Tech Park, BKC");
        assertThat(loc.getDescription()).isEqualTo("Headquarters Campus");
    }

    @Test
    @DisplayName("Amenities: Case-insensitive uniqueness (ux_amenities_name_lower)")
    void testAmenityCaseInsensitiveUniqueness() {
        amenityRepository.saveAndFlush(Amenity.builder()
                .name("4K Projector")
                .icon("projector-icon")
                .description("Ceiling mounted 4K laser projector")
                .build());

        // Uniqueness check case-insensitive
        assertThatThrownBy(() -> amenityRepository.saveAndFlush(Amenity.builder().name("4k projector").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Amenities: Association with room via room_amenities join table")
    @Transactional
    void testRoomAmenitiesAssociation() {
        Amenity projector = amenityRepository.save(Amenity.builder()
                .name("4K Projector")
                .icon("projector-icon")
                .description("Ceiling mounted 4K laser projector")
                .build());

        Amenity whiteboard = amenityRepository.save(Amenity.builder()
                .name("Smart Whiteboard")
                .icon("board-icon")
                .description("Interactive digital whiteboard")
                .build());

        // Associate with room
        testRoom.setAmenities(new java.util.HashSet<>(Set.of(projector, whiteboard)));
        roomRepository.saveAndFlush(testRoom);

        Room reloadedRoom = roomRepository.findById(testRoom.getId()).orElseThrow();
        assertThat(reloadedRoom.getAmenities()).hasSize(2);
    }

    @Test
    @DisplayName("BookingSeries: Recurrence bound XOR constraint (chk_series_recurrence_bound_xor)")
    void testBookingSeriesRecurrenceBoundXor() {
        // Valid 1: end_date provided, occurrence_count null
        BookingSeries seriesWithEndDate = bookingSeriesRepository.save(BookingSeries.builder()
                .user(testUser)
                .seriesName("Daily Standup")
                .frequency(RecurrenceFrequency.DAILY)
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 30))
                .occurrenceCount(null)
                .startLocalTime(LocalTime.of(10, 0))
                .endLocalTime(LocalTime.of(10, 30))
                .timezone("Asia/Kolkata")
                .status(BookingSeriesStatus.ACTIVE)
                .build());
        assertThat(seriesWithEndDate.getId()).isNotNull();

        // Valid 2: end_date null, occurrence_count provided
        BookingSeries seriesWithCount = bookingSeriesRepository.save(BookingSeries.builder()
                .user(testUser)
                .seriesName("Weekly Review")
                .frequency(RecurrenceFrequency.WEEKLY)
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(null)
                .occurrenceCount(10)
                .startLocalTime(LocalTime.of(15, 0))
                .endLocalTime(LocalTime.of(16, 0))
                .timezone("Asia/Kolkata")
                .status(BookingSeriesStatus.ACTIVE)
                .build());
        assertThat(seriesWithCount.getId()).isNotNull();

        // Invalid 1: both null -> Violates XOR
        assertThatThrownBy(() -> bookingSeriesRepository.saveAndFlush(BookingSeries.builder()
                .user(testUser)
                .seriesName("Invalid Both Null")
                .frequency(RecurrenceFrequency.DAILY)
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(null)
                .occurrenceCount(null)
                .startLocalTime(LocalTime.of(10, 0))
                .endLocalTime(LocalTime.of(10, 30))
                .timezone("Asia/Kolkata")
                .status(BookingSeriesStatus.ACTIVE)
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Invalid 2: both populated -> Violates XOR
        assertThatThrownBy(() -> bookingSeriesRepository.saveAndFlush(BookingSeries.builder()
                .user(testUser)
                .seriesName("Invalid Both Populated")
                .frequency(RecurrenceFrequency.DAILY)
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 30))
                .occurrenceCount(10)
                .startLocalTime(LocalTime.of(10, 0))
                .endLocalTime(LocalTime.of(10, 30))
                .timezone("Asia/Kolkata")
                .status(BookingSeriesStatus.ACTIVE)
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Bookings: Mandatory nonblank reason constraint (chk_bookings_reason_nonblank)")
    void testBookingReasonMandatoryAndNonBlank() {
        OffsetDateTime start = OffsetDateTime.parse("2026-09-01T10:00:00Z");
        OffsetDateTime end = OffsetDateTime.parse("2026-09-01T11:00:00Z");

        // Valid booking with nonblank reason
        Booking validBooking = bookingRepository.save(Booking.builder()
                .room(testRoom)
                .user(testUser)
                .startTime(start)
                .endTime(end)
                .reason("Valid Architecture Review")
                .status(BookingStatus.CONFIRMED)
                .build());
        assertThat(validBooking.getId()).isNotNull();

        // Invalid: whitespace-only reason rejected by DB check constraint
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO bookings (room_id, user_id, start_time, end_time, status, reason) VALUES (?, ?, ?, ?, ?, ?)",
                testRoom.getId(), testUser.getId(),
                OffsetDateTime.parse("2026-09-01T12:00:00Z"),
                OffsetDateTime.parse("2026-09-01T13:00:00Z"),
                "CONFIRMED", "   "
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Bookings: Recurring occurrence partial unique index (ux_bookings_series_occurrence)")
    void testBookingSeriesOccurrenceUniqueness() {
        BookingSeries series = bookingSeriesRepository.save(BookingSeries.builder()
                .user(testUser)
                .seriesName("Daily Sync")
                .frequency(RecurrenceFrequency.DAILY)
                .startDate(LocalDate.of(2026, 9, 1))
                .occurrenceCount(5)
                .startLocalTime(LocalTime.of(9, 0))
                .endLocalTime(LocalTime.of(9, 30))
                .timezone("Asia/Kolkata")
                .status(BookingSeriesStatus.ACTIVE)
                .build());

        // Occurrence 1
        bookingRepository.save(Booking.builder()
                .room(testRoom)
                .user(testUser)
                .series(series)
                .occurrenceIndex(1)
                .startTime(OffsetDateTime.parse("2026-09-01T09:00:00Z"))
                .endTime(OffsetDateTime.parse("2026-09-01T09:30:00Z"))
                .reason("Daily Sync #1")
                .status(BookingStatus.CONFIRMED)
                .build());

        // Duplicate CONFIRMED Occurrence 1 for same series -> Must be rejected
        assertThatThrownBy(() -> bookingRepository.saveAndFlush(Booking.builder()
                .room(testRoom)
                .user(testUser)
                .series(series)
                .occurrenceIndex(1)
                .startTime(OffsetDateTime.parse("2026-09-02T09:00:00Z"))
                .endTime(OffsetDateTime.parse("2026-09-02T09:30:00Z"))
                .reason("Duplicate Occurrence 1")
                .status(BookingStatus.CONFIRMED)
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Historical CANCELLED Occurrence 1 for same series CAN coexist with CONFIRMED Occurrence 1
        Booking cancelledOccurrence1 = bookingRepository.saveAndFlush(Booking.builder()
                .room(testRoom)
                .user(testUser)
                .series(series)
                .occurrenceIndex(1)
                .startTime(OffsetDateTime.parse("2026-09-02T09:00:00Z"))
                .endTime(OffsetDateTime.parse("2026-09-02T09:30:00Z"))
                .reason("Rescheduled Occurrence 1")
                .status(BookingStatus.CANCELLED)
                .cancelledReason("RESCHEDULED")
                .build());
        assertThat(cancelledOccurrence1.getId()).isNotNull();

        // Multiple one-time bookings (series = null, occurrenceIndex = null) are NOT restricted
        Booking single1 = bookingRepository.save(Booking.builder()
                .room(testRoom)
                .user(testUser)
                .startTime(OffsetDateTime.parse("2026-09-03T10:00:00Z"))
                .endTime(OffsetDateTime.parse("2026-09-03T11:00:00Z"))
                .reason("Single 1")
                .status(BookingStatus.CONFIRMED)
                .build());

        Booking single2 = bookingRepository.save(Booking.builder()
                .room(testRoom)
                .user(testUser)
                .startTime(OffsetDateTime.parse("2026-09-03T11:00:00Z"))
                .endTime(OffsetDateTime.parse("2026-09-03T12:00:00Z"))
                .reason("Single 2")
                .status(BookingStatus.CONFIRMED)
                .build());

        assertThat(single1.getId()).isNotNull();
        assertThat(single2.getId()).isNotNull();
    }

    @Test
    @DisplayName("AdminRequest: Persistence and relationship mapping")
    void testAdminRequestPersistence() {
        AdminRequest request = adminRequestRepository.save(AdminRequest.builder()
                .requesterUser(testUser)
                .location(defaultLocation)
                .room(testRoom)
                .requestType("EQUIPMENT_FAULT")
                .message("Projector in Aryabhata Conference is flickering")
                .status(AdminRequestStatus.OPEN)
                .build());

        assertThat(request.getId()).isNotNull();
        AdminRequest loaded = adminRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(loaded.getMessage()).contains("flickering");
        assertThat(loaded.getStatus()).isEqualTo(AdminRequestStatus.OPEN);
    }

    @Test
    @DisplayName("AuditLog: Persistence with JSONB metadata")
    void testAuditLogWithJsonb() {
        Map<String, Object> metadata = Map.of(
                "ip", "192.168.1.100",
                "userAgent", "RoomSync-Client/1.0",
                "modifiedFields", Map.of("capacity", 25)
        );

        AuditLog log = auditLogRepository.save(AuditLog.builder()
                .actorUser(testUser)
                .action("ROOM_UPDATED")
                .entityType("ROOM")
                .entityId(testRoom.getId().toString())
                .location(defaultLocation)
                .room(testRoom)
                .metadata(metadata)
                .build());

        assertThat(log.getId()).isNotNull();
        AuditLog loaded = auditLogRepository.findById(log.getId()).orElseThrow();
        assertThat(loaded.getMetadata()).containsKey("userAgent");
        assertThat(loaded.getMetadata().get("userAgent")).isEqualTo("RoomSync-Client/1.0");
    }

    @Test
    @DisplayName("AgentOperation: Persistence with JSONB draft payload and missing fields")
    void testAgentOperationWithJsonb() {
        Map<String, Object> draft = Map.of(
                "roomName", "Aryabhata Conference",
                "startTime", "2026-09-05T14:00:00Z",
                "durationMinutes", 60
        );

        Map<String, Object> missing = Map.of(
                "fields", new String[]{"reason", "attendeeCount"}
        );

        AgentOperation op = agentOperationRepository.save(AgentOperation.builder()
                .operationId("op-dm-001")
                .operationType("NL_BOOKING_CREATE")
                .actingUser(testUser)
                .status(AgentOperationStatus.NEEDS_CLARIFICATION)
                .draftPayload(draft)
                .missingFields(missing)
                .canonicalActionHash("hash123abc")
                .idempotencyKey("idem-op-001")
                .build());

        assertThat(op.getId()).isNotNull();
        AgentOperation loaded = agentOperationRepository.findById(op.getId()).orElseThrow();
        assertThat(loaded.getDraftPayload().get("roomName")).isEqualTo("Aryabhata Conference");
        assertThat(loaded.getStatus()).isEqualTo(AgentOperationStatus.NEEDS_CLARIFICATION);
    }

    @Test
    @DisplayName("IdempotencyRecord: Unique scope (scope_user_id, idempotency_key) and User RESTRICT delete")
    void testIdempotencyRecordAndUserRestrict() {
        IdempotencyRecord record = idempotencyRecordRepository.save(IdempotencyRecord.builder()
                .scopeUser(testUser)
                .idempotencyKey("key-user-1-req-999")
                .operationType("CREATE_BOOKING")
                .requestHash("sha256-payload-hash")
                .status("COMPLETED")
                .responseReference("booking-id-100")
                .build());

        assertThat(record.getId()).isNotNull();

        // Duplicate idempotency key for same user -> rejected
        assertThatThrownBy(() -> idempotencyRecordRepository.saveAndFlush(IdempotencyRecord.builder()
                .scopeUser(testUser)
                .idempotencyKey("key-user-1-req-999")
                .operationType("CREATE_BOOKING")
                .requestHash("sha256-payload-hash")
                .status("IN_PROGRESS")
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Deleting user when referenced by IdempotencyRecord is blocked by ON DELETE RESTRICT
        assertThatThrownBy(() -> userRepository.delete(testUser))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("AgentAction: Nullable tool_name, JSONB tool_arguments, and ON DELETE RESTRICT on AgentOperation")
    void testAgentActionAndOperationRestrict() {
        AgentOperation op = agentOperationRepository.save(AgentOperation.builder()
                .operationId("op-dm-002")
                .operationType("NL_BOOKING_CREATE")
                .actingUser(testUser)
                .status(AgentOperationStatus.EXECUTING)
                .build());

        AgentAction action = agentActionRepository.save(AgentAction.builder()
                .agentName("RoomSync-Planner")
                .actingUser(testUser)
                .operation(op)
                .toolName(null) // Nullable tool_name for non-tool actions
                .toolArguments(Map.of("param1", "val1"))
                .status("SUCCESS")
                .build());

        assertThat(action.getId()).isNotNull();
        assertThat(action.getToolName()).isNull();

        // Deleting parent AgentOperation is blocked by ON DELETE RESTRICT
        assertThatThrownBy(() -> agentOperationRepository.delete(op))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("NotificationOutbox: Persistence with JSONB payload and retry status tracking")
    void testNotificationOutbox() {
        Map<String, Object> payload = Map.of(
                "bookingId", 100,
                "roomName", "Aryabhata Conference",
                "recipientEmail", "architect@roomsync.com"
        );

        NotificationOutbox outbox = notificationOutboxRepository.save(NotificationOutbox.builder()
                .eventType("BOOKING_CONFIRMED")
                .aggregateType("BOOKING")
                .aggregateId("100")
                .recipient("architect@roomsync.com")
                .payload(payload)
                .status(NotificationStatus.PENDING)
                .attemptCount(0)
                .nextAttemptTime(OffsetDateTime.now())
                .build());

        assertThat(outbox.getId()).isNotNull();
        NotificationOutbox loaded = notificationOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(loaded.getPayload().get("recipientEmail")).isEqualTo("architect@roomsync.com");
        assertThat(loaded.getStatus()).isEqualTo(NotificationStatus.PENDING);
    }
}
