package com.roomsync.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomsync.location.dto.CreateLocationRequest;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityAuthorizationTest {

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
    private PasswordEncoder passwordEncoder;

    private Location location;
    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        location = locationRepository.findAll().stream().findFirst().orElseGet(() ->
                locationRepository.save(Location.builder()
                        .name("Mumbai Central")
                        .code("MUM-CTR")
                        .timezone("Asia/Kolkata")
                        .active(true)
                        .build())
        );

        Role userRole = roleRepository.findByName("USER").orElseGet(() ->
                roleRepository.save(Role.builder().name("USER").build())
        );

        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() ->
                roleRepository.save(Role.builder().name("ADMIN").build())
        );

        User normalUser = userRepository.save(User.builder()
                .name("Normal User")
                .email("user.auth@roomsync.com")
                .password(passwordEncoder.encode("secret"))
                .role(userRole)
                .location(location)
                .active(true)
                .build());

        User adminUser = userRepository.save(User.builder()
                .name("Admin User")
                .email("admin.auth@roomsync.com")
                .password(passwordEncoder.encode("secret"))
                .role(adminRole)
                .location(location)
                .active(true)
                .build());

        userToken = jwtTokenProvider.generateAccessToken(normalUser.getId(), normalUser.getEmail(), "USER", location.getId());
        adminToken = jwtTokenProvider.generateAccessToken(adminUser.getId(), adminUser.getEmail(), "ADMIN", location.getId());
    }

    @Test
    @DisplayName("Protected Endpoints - Unauthenticated request should receive 401 UNAUTHORIZED")
    void testUnauthenticatedAccessReturns401() throws Exception {
        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Protected Endpoints - Authenticated USER can access GET endpoints")
    void testUserCanAccessReadEndpoints() throws Exception {
        mockMvc.perform(get("/api/rooms")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/locations")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN Endpoints - Authenticated USER attempting ADMIN action should receive 403 FORBIDDEN")
    void testUserCannotAccessAdminEndpoints() throws Exception {
        CreateLocationRequest request = CreateLocationRequest.builder()
                .name("Delhi Branch")
                .code("DEL")
                .build();

        mockMvc.perform(post("/api/locations")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("ADMIN Endpoints - Authenticated ADMIN can perform administrative operations")
    void testAdminCanPerformAdminOperations() throws Exception {
        CreateLocationRequest request = CreateLocationRequest.builder()
                .name("Kolkata Branch")
                .code("CCU")
                .build();

        mockMvc.perform(post("/api/locations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Kolkata Branch"))
                .andExpect(jsonPath("$.code").value("CCU"));
    }
}
