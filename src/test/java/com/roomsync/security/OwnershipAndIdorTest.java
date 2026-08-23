package com.roomsync.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomsync.booking.dto.CreateBookingRequest;
import com.roomsync.booking.dto.RescheduleBookingRequest;
import com.roomsync.location.entity.Location;
import com.roomsync.location.repository.LocationRepository;
import com.roomsync.room.entity.Room;
import com.roomsync.room.entity.RoomStatus;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OwnershipAndIdorTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String userAToken;
    private String userBToken;
    private Room room;

    @BeforeEach
    void setUp() {
        Location location = locationRepository.findAll().stream().findFirst().orElseGet(() ->
                locationRepository.save(Location.builder()
                        .name("Mumbai Tech")
                        .code("MUM-TECH")
                        .timezone("Asia/Kolkata")
                        .active(true)
                        .build())
        );

        Role userRole = roleRepository.findByName("USER").orElseGet(() ->
                roleRepository.save(Role.builder().name("USER").build())
        );

        User userA = userRepository.save(User.builder()
                .name("User A")
                .email("usera@roomsync.com")
                .password(passwordEncoder.encode("secret"))
                .role(userRole)
                .location(location)
                .active(true)
                .build());

        User userB = userRepository.save(User.builder()
                .name("User B")
                .email("userb@roomsync.com")
                .password(passwordEncoder.encode("secret"))
                .role(userRole)
                .location(location)
                .active(true)
                .build());

        room = roomRepository.save(Room.builder()
                .name("Meeting Room 1")
                .capacity(10)
                .location(location)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build());

        userAToken = jwtTokenProvider.generateAccessToken(userA.getId(), userA.getEmail(), "USER", location.getId());
        userBToken = jwtTokenProvider.generateAccessToken(userB.getId(), userB.getEmail(), "USER", location.getId());
    }

    @Test
    @DisplayName("Ownership & IDOR - User B cannot view, modify, or cancel User A's booking")
    void testUserBCannotAccessUserABooking() throws Exception {
        // Step 1: User A creates a booking
        CreateBookingRequest createRequest = CreateBookingRequest.builder()
                .roomId(room.getId())
                .startTime(OffsetDateTime.parse("2026-08-25T10:00:00Z"))
                .endTime(OffsetDateTime.parse("2026-08-25T11:00:00Z"))
                .reason("User A Confidential Meeting")
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseJson = createResult.getResponse().getContentAsString();
        Long bookingId = objectMapper.readTree(responseJson).get("id").asLong();

        // Step 2: User A can view their own booking
        mockMvc.perform(get("/api/bookings/" + bookingId)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId));

        // Step 3: User B attempts to view User A's booking -> 403 FORBIDDEN
        mockMvc.perform(get("/api/bookings/" + bookingId)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        // Step 4: User B attempts to reschedule User A's booking -> 403 FORBIDDEN
        RescheduleBookingRequest rescheduleRequest = RescheduleBookingRequest.builder()
                .startTime(OffsetDateTime.parse("2026-08-25T14:00:00Z"))
                .endTime(OffsetDateTime.parse("2026-08-25T15:00:00Z"))
                .build();

        mockMvc.perform(put("/api/bookings/" + bookingId)
                        .header("Authorization", "Bearer " + userBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rescheduleRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        // Step 5: User B attempts to cancel User A's booking -> 403 FORBIDDEN
        mockMvc.perform(delete("/api/bookings/" + bookingId)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }
}
