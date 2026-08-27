package com.roomsync.room;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomsync.location.entity.Location;
import com.roomsync.location.repository.LocationRepository;
import com.roomsync.room.dto.CreateRoomRequest;
import com.roomsync.room.dto.RoomResponse;
import com.roomsync.room.dto.UpdateRoomRequest;
import com.roomsync.room.entity.Room;
import com.roomsync.room.repository.RoomRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Location location;
    private User adminUser;
    private String adminToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE bookings, rooms, users, locations RESTART IDENTITY CASCADE");

        location = locationRepository.save(Location.builder()
                .name("Mumbai")
                .code("MUM")
                .active(true)
                .timezone("Asia/Kolkata")
                .build());

        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() ->
                roleRepository.save(Role.builder().name("ADMIN").build()));

        adminUser = userRepository.save(User.builder()
                .name("Admin User")
                .email("admin@roomsync.com")
                .password("hash")
                .role(adminRole)
                .location(location)
                .build());

        adminToken = jwtTokenProvider.generateAccessToken(adminUser.getId(), adminUser.getEmail(), "ADMIN", location.getId());
    }

    @Test
    @DisplayName("End-to-End: Full Room lifecycle, soft deactivation, and location-scoped case-insensitive uniqueness")
    void testFullRoomLifecycle() throws Exception {
        // 1. Create Room
        CreateRoomRequest createRequest = CreateRoomRequest.builder()
                .locationId(location.getId())
                .name("Taj Mahal")
                .capacity(10)
                .description("Conference Hall")
                .build();

        String createResponseStr = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Taj Mahal"))
                .andExpect(jsonPath("$.capacity").value(10))
                .andExpect(jsonPath("$.location.code").value("MUM"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();

        RoomResponse createdRoom = objectMapper.readValue(createResponseStr, RoomResponse.class);
        Long roomId = createdRoom.getId();

        // 2. Reject Duplicate Name in same location (exact case)
        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));

        // 3. Reject Duplicate Name in same location (different case: "taj mahal")
        CreateRoomRequest lowerCaseRequest = CreateRoomRequest.builder()
                .locationId(location.getId())
                .name("taj mahal")
                .capacity(8)
                .build();

        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lowerCaseRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A room with the name 'taj mahal' already exists in location 'Mumbai'"));

        // 4. Get Room by ID
        mockMvc.perform(get("/api/rooms/" + roomId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(roomId))
                .andExpect(jsonPath("$.name").value("Taj Mahal"));

        // 5. Update Room
        UpdateRoomRequest updateRequest = UpdateRoomRequest.builder()
                .name("Taj Mahal Executive")
                .capacity(16)
                .description("Renovated Hall")
                .build();

        mockMvc.perform(put("/api/rooms/" + roomId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Taj Mahal Executive"))
                .andExpect(jsonPath("$.capacity").value(16));

        // 6. Lock Room
        mockMvc.perform(patch("/api/rooms/" + roomId + "/lock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));

        // Locking again should return 409
        mockMvc.perform(patch("/api/rooms/" + roomId + "/lock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        // 7. Unlock Room
        mockMvc.perform(patch("/api/rooms/" + roomId + "/unlock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        // Unlocking again should return 409
        mockMvc.perform(patch("/api/rooms/" + roomId + "/unlock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        // 8. Paginated Listing
        mockMvc.perform(get("/api/rooms?page=0&size=10&sort=name,asc")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Taj Mahal Executive"));

        // 9. Soft-Deactivate Room
        mockMvc.perform(delete("/api/rooms/" + roomId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // Confirm database row still physically exists with active = false
        Room dbRoom = roomRepository.findById(roomId).orElseThrow();
        assertThat(dbRoom.isActive()).isFalse();

        // 10. Inactive Room should not be returned by GET /api/rooms/{id}, but admin GET /api/rooms returns it with active=false
        mockMvc.perform(get("/api/rooms/" + roomId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/rooms")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].active").value(false));

        // 11. Deactivating already inactive room should return 409
        mockMvc.perform(delete("/api/rooms/" + roomId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        // 12. Inactive room name remains reserved in location
        CreateRoomRequest reCreateInactiveRequest = CreateRoomRequest.builder()
                .locationId(location.getId())
                .name("taj mahal executive")
                .capacity(12)
                .build();

        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reCreateInactiveRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A room with the name 'taj mahal executive' already exists in location 'Mumbai'"));
    }

    @Test
    @DisplayName("Database-level enforcement: PostgreSQL unique index ux_rooms_location_name_lower rejects case-insensitive duplicate in same location")
    void testDatabaseLevelCaseInsensitiveUniqueIndex() {
        jdbcTemplate.update("INSERT INTO rooms (location_id, name, capacity, status, active) VALUES (?, 'Qutub Minar', 8, 'AVAILABLE', true)", location.getId());

        assertThatThrownBy(() -> {
            jdbcTemplate.update("INSERT INTO rooms (location_id, name, capacity, status, active) VALUES (?, 'QUTUB MINAR', 12, 'AVAILABLE', true)", location.getId());
        }).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_rooms_location_name_lower");
    }
}
