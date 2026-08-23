package com.roomsync.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomsync.booking.dto.CreateBookingRequest;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HeaderSpoofingSecurityTest {

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

    private User userA;
    private User userB;
    private Room room;
    private String userAToken;

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

        userA = userRepository.save(User.builder()
                .name("User A")
                .email("user.a@roomsync.com")
                .password(passwordEncoder.encode("secret"))
                .role(userRole)
                .location(location)
                .active(true)
                .build());

        userB = userRepository.save(User.builder()
                .name("User B")
                .email("user.b@roomsync.com")
                .password(passwordEncoder.encode("secret"))
                .role(userRole)
                .location(location)
                .active(true)
                .build());

        room = roomRepository.save(Room.builder()
                .name("Spoof Test Room")
                .capacity(8)
                .location(location)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build());

        userAToken = jwtTokenProvider.generateAccessToken(userA.getId(), userA.getEmail(), "USER", location.getId());
    }

    @Test
    @DisplayName("Header Spoofing - Sending X-User-Id with User B's ID while authenticated as User A does NOT override identity")
    void testXUserIdHeaderCannotSpoofIdentity() throws Exception {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .roomId(room.getId())
                .startTime(OffsetDateTime.parse("2026-08-26T10:00:00Z"))
                .endTime(OffsetDateTime.parse("2026-08-26T11:00:00Z"))
                .reason("Anti-Spoofing Verification")
                .build();

        // Perform request with User A's JWT but sending spoofed X-User-Id: User B's ID
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + userAToken)
                        .header("X-User-Id", userB.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                // Assert the booking was created for User A (from JWT principal), NOT User B
                .andExpect(jsonPath("$.userId").value(userA.getId()));

        // Query /api/bookings/my with User A's token
        mockMvc.perform(get("/api/bookings/my")
                        .header("Authorization", "Bearer " + userAToken)
                        .header("X-User-Id", userB.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(userA.getId()));
    }
}
