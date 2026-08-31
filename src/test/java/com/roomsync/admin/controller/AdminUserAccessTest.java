package com.roomsync.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomsync.admin.dto.UpdateUserBookingAccessRequest;
import com.roomsync.audit.entity.AuditLog;
import com.roomsync.audit.repository.AuditLogRepository;
import com.roomsync.location.entity.Location;
import com.roomsync.location.repository.LocationRepository;
import com.roomsync.security.jwt.JwtTokenProvider;
import com.roomsync.user.entity.Role;
import com.roomsync.user.entity.User;
import com.roomsync.user.repository.RoleRepository;
import com.roomsync.user.repository.UserRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Location location;
    private Role adminRole;
    private Role userRole;
    private User adminUser;
    private User targetUser;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE notification_outbox, audit_logs, agent_actions, idempotency_records, agent_operations, bookings, booking_series, rooms, users, locations RESTART IDENTITY CASCADE");

        location = locationRepository.save(Location.builder()
                .name("Mumbai HQ")
                .code("MUM-HQ")
                .timezone("Asia/Kolkata")
                .active(true)
                .build());

        adminRole = roleRepository.findByName("ADMIN").orElseGet(() ->
                roleRepository.save(Role.builder().name("ADMIN").build()));

        userRole = roleRepository.findByName("USER").orElseGet(() ->
                roleRepository.save(Role.builder().name("USER").build()));

        adminUser = userRepository.save(User.builder()
                .wissenId("WT9999")
                .name("Admin Boss")
                .email("admin@wissen.com")
                .password("hashedPassword")
                .role(adminRole)
                .location(location)
                .active(true)
                .bookingEnabled(true)
                .build());

        targetUser = userRepository.save(User.builder()
                .wissenId("WT5128")
                .name("Alice Employee")
                .email("alice@wissen.com")
                .password("hashedPassword")
                .role(userRole)
                .location(location)
                .active(true)
                .bookingEnabled(true)
                .build());

        adminToken = jwtTokenProvider.generateAccessToken(adminUser.getId(), adminUser.getWissenId(), adminUser.getEmail(), "ADMIN", location.getId());
        userToken = jwtTokenProvider.generateAccessToken(targetUser.getId(), targetUser.getWissenId(), targetUser.getEmail(), "USER", location.getId());
    }

    @Test
    @DisplayName("Admin can list users with completely empty filters -> 200 OK without btrim(bytea) error")
    void testAdminListUsersWithNoFilters() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    @DisplayName("Admin can filter users by Wissen ID search")
    void testAdminListUsersSearchByWissenId() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", "WT5128"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].wissenId").value("WT5128"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Admin can filter users by Name search")
    void testAdminListUsersSearchByName() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].name").value("Alice Employee"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Admin can filter users by Email search")
    void testAdminListUsersSearchByEmail() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", "alice@wissen.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].email").value("alice@wissen.com"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Admin can filter users by bookingEnabled flag")
    void testAdminListUsersFilterByBookingEnabled() throws Exception {
        // Block targetUser
        targetUser.setBookingEnabled(false);
        userRepository.save(targetUser);

        // Query blocked users
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("bookingEnabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].wissenId").value("WT5128"))
                .andExpect(jsonPath("$.content[0].bookingEnabled").value(false));

        // Query enabled users
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("bookingEnabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].wissenId").value("WT9999"))
                .andExpect(jsonPath("$.content[0].bookingEnabled").value(true));
    }

    @Test
    @DisplayName("Admin can filter users by active flag")
    void testAdminListUsersFilterByActive() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("Admin can filter users by role")
    void testAdminListUsersFilterByRole() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].wissenId").value("WT9999"));
    }

    @Test
    @DisplayName("Admin can filter users by location ID")
    void testAdminListUsersFilterByLocation() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("locationId", location.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("Admin can block user booking access and audit log is recorded")
    void testAdminBlockUserBooking() throws Exception {
        UpdateUserBookingAccessRequest request = UpdateUserBookingAccessRequest.builder()
                .bookingEnabled(false)
                .reason("Excessive no-show policy violations")
                .build();

        mockMvc.perform(patch("/api/admin/users/" + targetUser.getId() + "/booking-access")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetUser.getId()))
                .andExpect(jsonPath("$.bookingEnabled").value(false));

        // Verify database state
        User updatedUser = userRepository.findById(targetUser.getId()).orElseThrow();
        assertThat(updatedUser.isBookingEnabled()).isFalse();

        // Verify audit log record
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).anyMatch(log ->
                "USER_BOOKING_BLOCKED".equals(log.getAction()) &&
                "USER".equals(log.getEntityType()) &&
                targetUser.getId().toString().equals(log.getEntityId()) &&
                adminUser.getId().equals(log.getActorUser().getId()) &&
                targetUser.getId().equals(log.getAffectedUser().getId())
        );
    }

    @Test
    @DisplayName("Admin can unblock user booking access")
    void testAdminUnblockUserBooking() throws Exception {
        // First set bookingEnabled to false
        targetUser.setBookingEnabled(false);
        userRepository.save(targetUser);

        UpdateUserBookingAccessRequest request = UpdateUserBookingAccessRequest.builder()
                .bookingEnabled(true)
                .reason("Suspension period ended")
                .build();

        mockMvc.perform(patch("/api/admin/users/" + targetUser.getId() + "/booking-access")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetUser.getId()))
                .andExpect(jsonPath("$.bookingEnabled").value(true));

        User updatedUser = userRepository.findById(targetUser.getId()).orElseThrow();
        assertThat(updatedUser.isBookingEnabled()).isTrue();

        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).anyMatch(log ->
                "USER_BOOKING_UNBLOCKED".equals(log.getAction()) &&
                targetUser.getId().toString().equals(log.getEntityId())
        );
    }

    @Test
    @DisplayName("Admin cannot block themselves -> 400 Bad Request and booking_enabled unchanged")
    void testAdminCannotBlockThemselves() throws Exception {
        UpdateUserBookingAccessRequest request = UpdateUserBookingAccessRequest.builder()
                .bookingEnabled(false)
                .reason("Accidental self-block attempt")
                .build();

        mockMvc.perform(patch("/api/admin/users/" + adminUser.getId() + "/booking-access")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Administrators cannot modify their own booking access permissions."));

        // Verify database state: admin's booking_enabled remains TRUE
        User persistedAdmin = userRepository.findById(adminUser.getId()).orElseThrow();
        assertThat(persistedAdmin.isBookingEnabled()).isTrue();

        // Verify no booking access audit log was created for adminUser
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).noneMatch(log ->
                ("USER_BOOKING_BLOCKED".equals(log.getAction()) || "USER_BOOKING_UNBLOCKED".equals(log.getAction())) &&
                adminUser.getId().toString().equals(log.getEntityId())
        );
    }

    @Test
    @DisplayName("Admin cannot unblock themselves -> 400 Bad Request and booking_enabled unchanged")
    void testAdminCannotUnblockThemselves() throws Exception {
        UpdateUserBookingAccessRequest request = UpdateUserBookingAccessRequest.builder()
                .bookingEnabled(true)
                .reason("Self-unblock attempt")
                .build();

        mockMvc.perform(patch("/api/admin/users/" + adminUser.getId() + "/booking-access")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Administrators cannot modify their own booking access permissions."));

        // Verify database state remains unchanged
        User persistedAdmin = userRepository.findById(adminUser.getId()).orElseThrow();
        assertThat(persistedAdmin.isBookingEnabled()).isTrue();
    }

    @Test
    @DisplayName("Non-admin user cannot access admin user endpoints -> 403 Forbidden")
    void testNonAdminCannotAccessUserManagement() throws Exception {
        UpdateUserBookingAccessRequest request = UpdateUserBookingAccessRequest.builder()
                .bookingEnabled(false)
                .reason("Malicious attempt")
                .build();

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/admin/users/" + targetUser.getId() + "/booking-access")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated request is rejected with 401 Unauthorized")
    void testUnauthenticatedAccessRejected() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }
}
