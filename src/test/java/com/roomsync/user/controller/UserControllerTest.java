package com.roomsync.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private Location location;
    private Role userRole;
    private User userA;
    private User userB;
    private String userAToken;

    @BeforeEach
    void setUp() {
        location = locationRepository.findAll().stream().findFirst().orElseGet(() ->
                locationRepository.save(Location.builder()
                        .name("Mumbai HQ")
                        .code("MUM-HQ")
                        .timezone("Asia/Kolkata")
                        .active(true)
                        .build())
        );

        userRole = roleRepository.findByName("USER").orElseGet(() ->
                roleRepository.save(Role.builder().name("USER").build())
        );

        userA = userRepository.save(User.builder()
                .wissenId("WT5128")
                .name("Alice Developer")
                .email("alice@wissen.com")
                .password("password123")
                .role(userRole)
                .location(location)
                .active(true)
                .build());

        userB = userRepository.save(User.builder()
                .wissenId("WI422")
                .name("Bob Consultant")
                .email("bob@wissen.com")
                .password("password123")
                .role(userRole)
                .location(location)
                .active(true)
                .build());

        userAToken = jwtTokenProvider.generateAccessToken(userA.getId(), userA.getWissenId(), userA.getEmail(), "USER", location.getId());
    }

    @Test
    @DisplayName("GET /api/users/me - Should return 200 and authoritative profile for authenticated user")
    void testGetAuthoritativeProfile() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userA.getId()))
                .andExpect(jsonPath("$.wissenId").value("WT5128"))
                .andExpect(jsonPath("$.name").value("Alice Developer"))
                .andExpect(jsonPath("$.email").value("alice@wissen.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.location.id").value(location.getId()))
                .andExpect(jsonPath("$.location.name").value(location.getName()))
                .andExpect(jsonPath("$.location.code").value(location.getCode()))
                .andExpect(jsonPath("$.location.timezone").value(location.getTimezone()))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.bookingEnabled").value(true))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/users/me - Should return 401 Unauthorized when no JWT is provided")
    void testGetProfileUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/users/me - Anti-Spoofing: Sending X-User-Id with User B's ID while authenticated as User A still returns User A profile")
    void testGetProfileWithSpoofedHeader() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + userAToken)
                        .header("X-User-Id", userB.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userA.getId()))
                .andExpect(jsonPath("$.wissenId").value("WT5128"))
                .andExpect(jsonPath("$.name").value("Alice Developer"));
    }
}
