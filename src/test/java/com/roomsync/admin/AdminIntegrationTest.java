package com.roomsync.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomsync.admin.dto.ResolveAdminRequestRequest;
import com.roomsync.admin.entity.AdminRequest;
import com.roomsync.admin.entity.AdminRequestStatus;
import com.roomsync.admin.repository.AdminRequestRepository;
import com.roomsync.booking.entity.Booking;
import com.roomsync.booking.entity.BookingStatus;
import com.roomsync.booking.repository.BookingRepository;
import com.roomsync.location.dto.CreateLocationRequest;
import com.roomsync.location.entity.Location;
import com.roomsync.location.repository.LocationRepository;
import com.roomsync.room.dto.CreateAmenityRequest;
import com.roomsync.room.dto.CreateRoomRequest;
import com.roomsync.room.dto.UpdateAmenityRequest;
import com.roomsync.room.entity.Amenity;
import com.roomsync.room.entity.Room;
import com.roomsync.room.entity.RoomStatus;
import com.roomsync.room.repository.AmenityRepository;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private AdminRequestRepository adminRequestRepository;

    @Autowired
    private AmenityRepository amenityRepository;

    @Autowired
    private com.roomsync.audit.repository.AuditLogRepository auditLogRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Location locationMumbai;
    private User adminUser;
    private User regularUser;
    private Room roomAlpha;
    private Room roomBeta;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE admin_requests, bookings, booking_series, room_amenities, amenities, rooms, users, locations RESTART IDENTITY CASCADE");

        locationMumbai = locationRepository.save(Location.builder()
                .name("Mumbai HQ")
                .code("MUM")
                .timezone("Asia/Kolkata")
                .active(true)
                .build());

        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() ->
                roleRepository.save(Role.builder().name("ADMIN").build()));

        Role userRole = roleRepository.findByName("USER").orElseGet(() ->
                roleRepository.save(Role.builder().name("USER").build()));

        adminUser = userRepository.save(User.builder()
                .wissenId("WT1001")
                .name("Admin User")
                .email("admin@roomsync.com")
                .password("hashAdmin")
                .role(adminRole)
                .location(locationMumbai)
                .active(true)
                .build());

        regularUser = userRepository.save(User.builder()
                .wissenId("WT1002")
                .name("User Alice")
                .email("alice@roomsync.com")
                .password("hashAlice")
                .role(userRole)
                .location(locationMumbai)
                .active(true)
                .build());

        roomAlpha = roomRepository.save(Room.builder()
                .name("Room Alpha")
                .location(locationMumbai)
                .capacity(10)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build());

        roomBeta = roomRepository.save(Room.builder()
                .name("Room Beta")
                .location(locationMumbai)
                .capacity(16)
                .status(RoomStatus.LOCKED)
                .active(true)
                .build());

        adminToken = jwtTokenProvider.generateAccessToken(adminUser.getId(), adminUser.getWissenId(), adminUser.getEmail(), "ADMIN", locationMumbai.getId());
        userToken = jwtTokenProvider.generateAccessToken(regularUser.getId(), regularUser.getWissenId(), regularUser.getEmail(), "USER", locationMumbai.getId());
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("TRUNCATE admin_requests, bookings, booking_series, room_amenities, amenities, rooms, users, locations RESTART IDENTITY CASCADE");
    }

    @Test
    @DisplayName("Admin Security: USER receives 403, Unauthenticated receives 401, ADMIN receives 200 on /api/admin/occupancy")
    void testAdminSecurityOnOccupancy() throws Exception {
        // 1. Unauthenticated -> 401
        mockMvc.perform(get("/api/admin/occupancy"))
                .andExpect(status().isUnauthorized());

        // 2. USER -> 403
        mockMvc.perform(get("/api/admin/occupancy")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        // 3. ADMIN -> 200
        mockMvc.perform(get("/api/admin/occupancy")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    @DisplayName("Occupancy Derivation: Dynamically identifies AVAILABLE, LOCKED, and OCCUPIED rooms without mutating DB")
    void testOccupancyDerivation() throws Exception {
        // Create an active booking right now for Room Alpha
        OffsetDateTime now = OffsetDateTime.now();
        bookingRepository.save(Booking.builder()
                .room(roomAlpha)
                .user(regularUser)
                .startTime(now.minusHours(1))
                .endTime(now.plusHours(1))
                .reason("Live Strategy Session")
                .status(BookingStatus.CONFIRMED)
                .build());

        mockMvc.perform(get("/api/admin/occupancy")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].roomId").value(roomAlpha.getId()))
                .andExpect(jsonPath("$.content[0].administrativeState").value("AVAILABLE"))
                .andExpect(jsonPath("$.content[0].occupancyStatus").value("OCCUPIED"))
                .andExpect(jsonPath("$.content[0].currentBooking.userName").value("User Alice"))
                .andExpect(jsonPath("$.content[0].currentBooking.userWissenId").value("WT1002"))
                .andExpect(jsonPath("$.content[1].roomId").value(roomBeta.getId()))
                .andExpect(jsonPath("$.content[1].administrativeState").value("LOCKED"))
                .andExpect(jsonPath("$.content[1].occupancyStatus").value("LOCKED"));

        // Verify RoomAlpha in DB is STILL 'AVAILABLE'
        Room roomDb = roomRepository.findById(roomAlpha.getId()).orElseThrow();
        assertThat(roomDb.getStatus()).isEqualTo(RoomStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Analytics Utilization: Admin endpoint computes accurate utilization")
    void testAdminUtilization() throws Exception {
        mockMvc.perform(get("/api/admin/utilization")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("startDate", "2026-09-01")
                        .param("endDate", "2026-09-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value("2026-09-01"))
                .andExpect(jsonPath("$.endDate").value("2026-09-02"))
                .andExpect(jsonPath("$.rooms").isArray())
                .andExpect(jsonPath("$.rooms.length()").value(2));

        // User forbidden
        mockMvc.perform(get("/api/admin/utilization")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Amenity Management: Complete CRUD and authorization")
    void testAmenityCrudAndAuth() throws Exception {
        CreateAmenityRequest createReq = CreateAmenityRequest.builder()
                .name("Whiteboard")
                .icon("board-icon")
                .description("Magnetic Whiteboard")
                .build();

        // 1. USER cannot create amenity -> 403
        mockMvc.perform(post("/api/amenities")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isForbidden());

        // 2. ADMIN creates amenity -> 201
        String resp = mockMvc.perform(post("/api/amenities")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Whiteboard"))
                .andReturn().getResponse().getContentAsString();

        Long amenityId = objectMapper.readTree(resp).get("id").asLong();

        // 3. Authenticated USER can read amenities -> 200
        mockMvc.perform(get("/api/amenities/" + amenityId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Whiteboard"));

        // 4. ADMIN updates amenity -> 200
        UpdateAmenityRequest updateReq = UpdateAmenityRequest.builder()
                .name("Digital Whiteboard")
                .build();

        mockMvc.perform(put("/api/amenities/" + amenityId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Digital Whiteboard"));

        // 5. ADMIN deletes amenity -> 204
        mockMvc.perform(delete("/api/amenities/" + amenityId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(amenityRepository.findById(amenityId)).isEmpty();
    }

    @Test
    @DisplayName("Admin Requests: Listing, viewing, and resolving requests")
    void testAdminRequestWorkflow() throws Exception {
        AdminRequest request = adminRequestRepository.save(AdminRequest.builder()
                .requesterUser(regularUser)
                .location(locationMumbai)
                .room(roomAlpha)
                .requestType("RECURRING_CONFLICT")
                .message("Please approve room override")
                .status(AdminRequestStatus.OPEN)
                .build());

        // 1. List requests
        mockMvc.perform(get("/api/admin/requests")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(request.getId()))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"))
                .andExpect(jsonPath("$.content[0].requesterUserWissenId").value("WT1002"));

        // 2. View single request
        mockMvc.perform(get("/api/admin/requests/" + request.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Please approve room override"))
                .andExpect(jsonPath("$.requesterUserWissenId").value("WT1002"));

        // 3. Resolve request
        ResolveAdminRequestRequest resolveDto = ResolveAdminRequestRequest.builder()
                .status(AdminRequestStatus.RESOLVED)
                .resolutionNotes("Approved and booked alternate room")
                .build();

        mockMvc.perform(patch("/api/admin/requests/" + request.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedByUserId").value(adminUser.getId()))
                .andExpect(jsonPath("$.resolvedByUserWissenId").value("WT1001"));
    }

    @Test
    @DisplayName("Room & Location Administrative Operations: Activate, deactivate, lock, unlock")
    void testRoomAndLocationAdministration() throws Exception {
        // 1. Lock room
        mockMvc.perform(patch("/api/rooms/" + roomAlpha.getId() + "/lock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));

        // 2. Unlock room
        mockMvc.perform(patch("/api/rooms/" + roomAlpha.getId() + "/unlock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        // 3. Deactivate room
        mockMvc.perform(patch("/api/rooms/" + roomAlpha.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // 4. Activate room
        mockMvc.perform(patch("/api/rooms/" + roomAlpha.getId() + "/activate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("Admin Audit Logs: Admin can query paginated audit logs with filters, normal user gets 403")
    void testAdminAuditLogs() throws Exception {
        Booking booking = bookingRepository.save(Booking.builder()
                .room(roomAlpha)
                .user(regularUser)
                .startTime(OffsetDateTime.now().plusHours(1))
                .endTime(OffsetDateTime.now().plusHours(2))
                .status(BookingStatus.CONFIRMED)
                .reason("Quarterly Review")
                .build());

        // Create an audit log record
        com.roomsync.audit.entity.AuditLog auditLog = com.roomsync.audit.entity.AuditLog.builder()
                .actorUser(adminUser)
                .affectedUser(regularUser)
                .action("BOOKING_CREATED")
                .entityType("BOOKING")
                .entityId(booking.getId().toString())
                .location(locationMumbai)
                .room(roomAlpha)
                .booking(booking)
                .metadata(java.util.Map.of("reason", "Quarterly Review", "actorType", "ADMIN"))
                .build();
        auditLogRepository.save(auditLog);

        // 1. Normal user should be rejected (403 Forbidden)
        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        // 2. Admin should successfully retrieve audit logs
        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].action").value("BOOKING_CREATED"))
                .andExpect(jsonPath("$.content[0].actorUserId").value(adminUser.getId()))
                .andExpect(jsonPath("$.content[0].actorWissenId").value("WT1001"))
                .andExpect(jsonPath("$.content[0].actorEmail").value(adminUser.getEmail()))
                .andExpect(jsonPath("$.content[0].affectedUserId").value(regularUser.getId()))
                .andExpect(jsonPath("$.content[0].affectedUserWissenId").value("WT1002"))
                .andExpect(jsonPath("$.content[0].entityType").value("BOOKING"))
                .andExpect(jsonPath("$.totalElements").isNumber());

        // 3. Admin filter by action
        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("action", "BOOKING_CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("BOOKING_CREATED"));
    }

    @Test
    @DisplayName("Admin Bookings API: returns actual database wissenId for booking owner")
    void testAdminGetBookingsReturnsActualDatabaseWissenId() throws Exception {
        Role userRole = roleRepository.findByName("USER").orElseThrow();
        User userA = userRepository.save(User.builder()
                .wissenId("WT900014")
                .name("User A")
                .email("userA@roomsync.com")
                .password("hashUserA")
                .role(userRole)
                .location(locationMumbai)
                .active(true)
                .build());

        Booking bookingA = bookingRepository.save(Booking.builder()
                .room(roomAlpha)
                .user(userA)
                .startTime(OffsetDateTime.now().plusDays(1))
                .endTime(OffsetDateTime.now().plusDays(1).plusHours(1))
                .status(BookingStatus.CONFIRMED)
                .reason("Quarterly Planning")
                .build());

        // GET /api/admin/bookings
        mockMvc.perform(get("/api/admin/bookings")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("userId", userA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(bookingA.getId()))
                .andExpect(jsonPath("$.content[0].userId").value(userA.getId()))
                .andExpect(jsonPath("$.content[0].wissenId").value("WT900014"))
                .andExpect(jsonPath("$.content[0].userWissenId").value("WT900014"))
                .andExpect(jsonPath("$.content[0].userName").value("User A"));

        // GET /api/admin/bookings/{id}
        mockMvc.perform(get("/api/admin/bookings/" + bookingA.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingA.getId()))
                .andExpect(jsonPath("$.userId").value(userA.getId()))
                .andExpect(jsonPath("$.wissenId").value("WT900014"))
                .andExpect(jsonPath("$.userWissenId").value("WT900014"))
                .andExpect(jsonPath("$.userName").value("User A"));
    }
}
