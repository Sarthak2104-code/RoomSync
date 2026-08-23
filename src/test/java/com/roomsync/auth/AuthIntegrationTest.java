package com.roomsync.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomsync.auth.dto.LoginRequest;
import com.roomsync.auth.dto.RefreshTokenRequest;
import com.roomsync.location.entity.Location;
import com.roomsync.location.repository.LocationRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Location testLocation;
    private Role userRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        testLocation = locationRepository.findAll().stream().findFirst().orElseGet(() ->
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

        adminRole = roleRepository.findByName("ADMIN").orElseGet(() ->
                roleRepository.save(Role.builder().name("ADMIN").build())
        );
    }

    @Test
    @DisplayName("POST /api/auth/login - Should successfully authenticate active user and return JWT tokens")
    void testSuccessfulLogin() throws Exception {
        User user = userRepository.save(User.builder()
                .name("Alice User")
                .email("alice@roomsync.com")
                .password(passwordEncoder.encode("secret123"))
                .role(userRole)
                .location(testLocation)
                .active(true)
                .build());

        LoginRequest request = LoginRequest.builder()
                .email("alice@roomsync.com")
                .password("secret123")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.email").value("alice@roomsync.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("POST /api/auth/login - Should return 401 when password is invalid")
    void testLoginWithInvalidPassword() throws Exception {
        userRepository.save(User.builder()
                .name("Bob User")
                .email("bob@roomsync.com")
                .password(passwordEncoder.encode("correct-password"))
                .role(userRole)
                .location(testLocation)
                .active(true)
                .build());

        LoginRequest request = LoginRequest.builder()
                .email("bob@roomsync.com")
                .password("wrong-password")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("POST /api/auth/login - Should return 401 when user does not exist")
    void testLoginWithNonexistentUser() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("nonexistent@roomsync.com")
                .password("any-password")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /api/auth/login - Should return 401 when user is inactive")
    void testLoginWithInactiveUser() throws Exception {
        userRepository.save(User.builder()
                .name("Inactive User")
                .email("inactive@roomsync.com")
                .password(passwordEncoder.encode("secret123"))
                .role(userRole)
                .location(testLocation)
                .active(false)
                .build());

        LoginRequest request = LoginRequest.builder()
                .email("inactive@roomsync.com")
                .password("secret123")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("User account is inactive"));
    }

    @Test
    @DisplayName("POST /api/auth/refresh - Should issue new access token using valid refresh token")
    void testRefreshTokenSuccess() throws Exception {
        User user = userRepository.save(User.builder()
                .name("Charlie User")
                .email("charlie@roomsync.com")
                .password(passwordEncoder.encode("secret123"))
                .role(userRole)
                .location(testLocation)
                .active(true)
                .build());

        LoginRequest loginRequest = LoginRequest.builder()
                .email("charlie@roomsync.com")
                .password("secret123")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = loginResult.getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(responseJson).get("refreshToken").asText();

        RefreshTokenRequest refreshRequest = RefreshTokenRequest.builder()
                .refreshToken(refreshToken)
                .build();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }
}
