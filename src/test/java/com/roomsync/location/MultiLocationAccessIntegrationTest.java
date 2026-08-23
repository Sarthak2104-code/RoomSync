package com.roomsync.location;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomsync.booking.dto.CreateBookingRequest;
import com.roomsync.location.dto.CreateLocationRequest;
import com.roomsync.location.entity.Location;
import com.roomsync.location.repository.LocationRepository;
import com.roomsync.room.dto.CreateRoomRequest;
import com.roomsync.room.dto.RoomResponse;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MultiLocationAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Location mumbaiLoc;
    private Location puneLoc;
    private User mumbaiUser;
    private User puneUser;
    private User adminUser;
    private String mumbaiToken;
    private String puneToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE bookings, rooms, users, locations RESTART IDENTITY CASCADE");

        mumbaiLoc = locationRepository.save(Location.builder().name("Mumbai").code("MUM").active(true).timezone("Asia/Kolkata").build());
        puneLoc = locationRepository.save(Location.builder().name("Pune").code("PUN").active(true).timezone("Asia/Kolkata").build());

        Role userRole = roleRepository.findByName("USER").orElseGet(() ->
                roleRepository.save(Role.builder().name("USER").build()));
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() ->
                roleRepository.save(Role.builder().name("ADMIN").build()));

        mumbaiUser = userRepository.save(User.builder()
                .name("Alice Mumbai")
                .email("alice@mumbai.com")
                .password("hash")
                .role(userRole)
                .location(mumbaiLoc)
                .build());

        puneUser = userRepository.save(User.builder()
                .name("Bob Pune")
                .email("bob@pune.com")
                .password("hash")
                .role(userRole)
                .location(puneLoc)
                .build());

        adminUser = userRepository.save(User.builder()
                .name("Global Admin")
                .email("admin@roomsync.com")
                .password("hash")
                .role(adminRole)
                .location(mumbaiLoc)
                .build());

        mumbaiToken = jwtTokenProvider.generateAccessToken(mumbaiUser.getId(), mumbaiUser.getEmail(), "USER", mumbaiLoc.getId());
        puneToken = jwtTokenProvider.generateAccessToken(puneUser.getId(), puneUser.getEmail(), "USER", puneLoc.getId());
        adminToken = jwtTokenProvider.generateAccessToken(adminUser.getId(), adminUser.getEmail(), "ADMIN", mumbaiLoc.getId());
    }

    @Test
    @DisplayName("Location-Scoped Uniqueness: Same room name allowed across different locations, rejected within same location")
    void testLocationScopedRoomNameUniqueness() throws Exception {
        CreateRoomRequest mumbaiRoomReq = CreateRoomRequest.builder()
                .locationId(mumbaiLoc.getId())
                .name("Boardroom Alpha")
                .capacity(12)
                .build();

        // 1. Create Boardroom Alpha in Mumbai -> 201 Created
        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mumbaiRoomReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Boardroom Alpha"))
                .andExpect(jsonPath("$.location.code").value("MUM"));

        // 2. Create Boardroom Alpha in Pune -> 201 Created (Allowed!)
        CreateRoomRequest puneRoomReq = CreateRoomRequest.builder()
                .locationId(puneLoc.getId())
                .name("Boardroom Alpha")
                .capacity(16)
                .build();

        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(puneRoomReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Boardroom Alpha"))
                .andExpect(jsonPath("$.location.code").value("PUN"));

        // 3. Attempt duplicate "boardroom alpha" in Mumbai -> 409 Conflict
        CreateRoomRequest duplicateMumbaiReq = CreateRoomRequest.builder()
                .locationId(mumbaiLoc.getId())
                .name("boardroom alpha")
                .capacity(10)
                .build();

        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateMumbaiReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    @DisplayName("Cross-Location Authorization: Normal users can only access their own location, Admins access all")
    void testCrossLocationAuthorization() throws Exception {
        // Create Room in Mumbai (by Admin)
        String mumbaiResp = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateRoomRequest.builder()
                                .locationId(mumbaiLoc.getId())
                                .name("Gateway Room")
                                .capacity(10)
                                .build())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long mumbaiRoomId = objectMapper.readValue(mumbaiResp, RoomResponse.class).getId();

        // Create Room in Pune (by Admin)
        String puneResp = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateRoomRequest.builder()
                                .locationId(puneLoc.getId())
                                .name("Shaniwar Room")
                                .capacity(15)
                                .build())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long puneRoomId = objectMapper.readValue(puneResp, RoomResponse.class).getId();

        // Mumbai User accesses Mumbai Room -> 200 OK
        mockMvc.perform(get("/api/rooms/" + mumbaiRoomId).header("Authorization", "Bearer " + mumbaiToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mumbaiRoomId));

        // Mumbai User accesses Pune Room -> 403 Forbidden
        mockMvc.perform(get("/api/rooms/" + puneRoomId).header("Authorization", "Bearer " + mumbaiToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        // Pune User accesses Pune Room -> 200 OK
        mockMvc.perform(get("/api/rooms/" + puneRoomId).header("Authorization", "Bearer " + puneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(puneRoomId));

        // Pune User accesses Mumbai Room -> 403 Forbidden
        mockMvc.perform(get("/api/rooms/" + mumbaiRoomId).header("Authorization", "Bearer " + puneToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        // Admin User accesses Mumbai & Pune Rooms -> 200 OK
        mockMvc.perform(get("/api/rooms/" + mumbaiRoomId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/rooms/" + puneRoomId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Booking Cross-Location Authorization
        OffsetDateTime startTime = OffsetDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime endTime = startTime.plusHours(1);

        // Alice (Mumbai) books Mumbai Room -> 201 Created
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + mumbaiToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateBookingRequest.builder()
                                .roomId(mumbaiRoomId)
                                .startTime(startTime)
                                .endTime(endTime)
                                .reason("Mumbai Project Kickoff")
                                .build())))
                .andExpect(status().isCreated());

        // Alice (Mumbai) attempts to book Pune Room -> 403 Forbidden
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + mumbaiToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateBookingRequest.builder()
                                .roomId(puneRoomId)
                                .startTime(startTime)
                                .endTime(endTime)
                                .reason("Cross-location booking attempt")
                                .build())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        // Admin books Pune Room -> 201 Created
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateBookingRequest.builder()
                                .roomId(puneRoomId)
                                .startTime(startTime)
                                .endTime(endTime)
                                .reason("Admin Inspection")
                                .build())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Location Deactivation: Preserves existing rooms and blocks new room creation and booking")
    void testLocationDeactivationBehavior() throws Exception {
        // 1. Create Room in Pune (by Admin)
        String puneResp = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateRoomRequest.builder()
                                .locationId(puneLoc.getId())
                                .name("Sinhagad Room")
                                .capacity(10)
                                .build())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long puneRoomId = objectMapper.readValue(puneResp, RoomResponse.class).getId();

        // 2. Soft-deactivate Pune location (by Admin)
        mockMvc.perform(delete("/api/locations/" + puneLoc.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        Location deactivatedLoc = locationRepository.findById(puneLoc.getId()).orElseThrow();
        assertThat(deactivatedLoc.isActive()).isFalse();

        // 3. Pune Room still physically exists in database
        Room dbRoom = roomRepository.findById(puneRoomId).orElseThrow();
        assertThat(dbRoom).isNotNull();

        // 4. Attempt to create new room in deactivated Pune location -> 409 Conflict
        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateRoomRequest.builder()
                                .locationId(puneLoc.getId())
                                .name("New Pune Room")
                                .capacity(8)
                                .build())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("inactive")));

        // 5. Attempt to book room in deactivated Pune location -> 409 Conflict
        OffsetDateTime startTime = OffsetDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime endTime = startTime.plusHours(1);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateBookingRequest.builder()
                                .roomId(puneRoomId)
                                .startTime(startTime)
                                .endTime(endTime)
                                .reason("Booking in inactive location")
                                .build())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("inactive")));
    }

    @Test
    @DisplayName("Independent Concurrency: Bookings in Mumbai and Pune execute without cross-location blocking")
    void testConcurrentBookingsAcrossLocationsDoNotBlock() throws Exception {
        // Create Mumbai Room and Pune Room
        Room mumbaiRoom = roomRepository.save(Room.builder().location(mumbaiLoc).name("Mumbai 1").capacity(10).status(com.roomsync.room.entity.RoomStatus.AVAILABLE).active(true).build());
        Room puneRoom = roomRepository.save(Room.builder().location(puneLoc).name("Pune 1").capacity(10).status(com.roomsync.room.entity.RoomStatus.AVAILABLE).active(true).build());

        OffsetDateTime start = OffsetDateTime.parse("2026-08-30T10:00:00Z");
        OffsetDateTime end = OffsetDateTime.parse("2026-08-30T11:00:00Z");

        CreateBookingRequest mumReq = CreateBookingRequest.builder().roomId(mumbaiRoom.getId()).startTime(start).endTime(end).reason("Mumbai Sync").build();
        CreateBookingRequest punReq = CreateBookingRequest.builder().roomId(puneRoom.getId()).startTime(start).endTime(end).reason("Pune Sync").build();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);

        Future<?> f1 = executor.submit(() -> {
            try {
                startGate.await();
                int status = mockMvc.perform(post("/api/bookings")
                                .header("Authorization", "Bearer " + mumbaiToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(mumReq)))
                        .andReturn().getResponse().getStatus();
                if (status == 201) successCount.incrementAndGet();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Future<?> f2 = executor.submit(() -> {
            try {
                startGate.await();
                int status = mockMvc.perform(post("/api/bookings")
                                .header("Authorization", "Bearer " + puneToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(punReq)))
                        .andReturn().getResponse().getStatus();
                if (status == 201) successCount.incrementAndGet();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        startGate.countDown();
        f1.get(5, TimeUnit.SECONDS);
        f2.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(2);
    }
}
