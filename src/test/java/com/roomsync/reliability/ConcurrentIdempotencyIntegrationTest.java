package com.roomsync.reliability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomsync.agent.repository.AgentOperationRepository;
import com.roomsync.booking.dto.CreateBookingRequest;
import com.roomsync.booking.repository.BookingRepository;
import com.roomsync.location.entity.Location;
import com.roomsync.location.repository.LocationRepository;
import com.roomsync.reliability.repository.IdempotencyRecordRepository;
import com.roomsync.room.entity.Room;
import com.roomsync.room.entity.RoomStatus;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ConcurrentIdempotencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private AgentOperationRepository agentOperationRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.roomsync.reliability.filter.RateLimitingFilter rateLimitingFilter;

    private User testUser;
    private User otherUser;
    private Location testLocation;
    private Room testRoom;
    private String userToken;
    private String otherToken;

    @BeforeEach
    void setUp() {
        if (rateLimitingFilter != null) rateLimitingFilter.clearBuckets();
        jdbcTemplate.execute("TRUNCATE notification_outbox, audit_logs, agent_actions, idempotency_records, agent_operations, bookings, booking_series, rooms, users, locations RESTART IDENTITY CASCADE");

        testLocation = locationRepository.save(Location.builder()
                .name("Mumbai HQ")
                .code("MUM")
                .timezone("Asia/Kolkata")
                .active(true)
                .build());

        Role userRole = roleRepository.findByName("USER").orElseGet(() ->
                roleRepository.save(Role.builder().name("USER").build()));

        testUser = userRepository.save(User.builder()
                .wissenId("WT1111")
                .name("Alice")
                .email("alice@roomsync.com")
                .password("hashPass")
                .role(userRole)
                .location(testLocation)
                .active(true)
                .build());

        otherUser = userRepository.save(User.builder()
                .wissenId("WT1112")
                .name("Bob")
                .email("bob@roomsync.com")
                .password("hashPass")
                .role(userRole)
                .location(testLocation)
                .active(true)
                .build());

        testRoom = roomRepository.save(Room.builder()
                .name("Room Alpha")
                .capacity(10)
                .location(testLocation)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build());

        userToken = jwtTokenProvider.generateAccessToken(testUser.getId(), testUser.getWissenId(), testUser.getEmail(), "USER", testLocation.getId());
        otherToken = jwtTokenProvider.generateAccessToken(otherUser.getId(), otherUser.getWissenId(), otherUser.getEmail(), "USER", testLocation.getId());
    }

    @AfterEach
    void tearDown() {
        if (rateLimitingFilter != null) rateLimitingFilter.clearBuckets();
        jdbcTemplate.execute("TRUNCATE notification_outbox, audit_logs, agent_actions, idempotency_records, agent_operations, bookings, booking_series, rooms, users, locations RESTART IDENTITY CASCADE");
    }

    @Test
    @DisplayName("B. 100 concurrent identical booking requests with same Idempotency-Key create exactly 1 booking and 1 operation")
    void test100ConcurrentIdenticalRequests() throws Exception {
        int threads = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        CreateBookingRequest request = CreateBookingRequest.builder()
                .roomId(testRoom.getId())
                .startTime(OffsetDateTime.parse("2026-09-10T04:30:00Z")) // 10:00 IST
                .endTime(OffsetDateTime.parse("2026-09-10T05:30:00Z"))   // 11:00 IST
                .reason("Critical Architecture Sprint")
                .build();

        String payloadJson = objectMapper.writeValueAsString(request);
        String idempotencyKey = "CONCURRENT-KEY-001";

        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());
        List<Long> returnedBookingIds = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    MvcResult result = mockMvc.perform(post("/api/bookings")
                                    .header("Authorization", "Bearer " + userToken)
                                    .header("Idempotency-Key", idempotencyKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(payloadJson))
                            .andReturn();

                    int status = result.getResponse().getStatus();
                    statusCodes.add(status);

                    if (status == 201 || status == 200) {
                        Long bookingId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
                        returnedBookingIds.add(bookingId);
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        latch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly 1 booking row must exist in the database!
        assertThat(bookingRepository.count()).isEqualTo(1);
        // Exactly 1 idempotency record must exist!
        assertThat(idempotencyRecordRepository.count()).isEqualTo(1);
        // Exactly 1 operation must exist!
        assertThat(agentOperationRepository.count()).isEqualTo(1);

        // Every successful response references the EXACT same booking ID
        assertThat(returnedBookingIds).isNotEmpty();
        Long authoritativeBookingId = returnedBookingIds.get(0);
        for (Long bId : returnedBookingIds) {
            assertThat(bId).isEqualTo(authoritativeBookingId);
        }
    }

    @Test
    @DisplayName("C. Same user + same key + different request payload returns HTTP 409 IDEMPOTENCY_CONFLICT")
    void testSameKeyDifferentPayloadReturns409() throws Exception {
        String idempotencyKey = "DIFF-PAYLOAD-KEY";

        CreateBookingRequest request1 = CreateBookingRequest.builder()
                .roomId(testRoom.getId())
                .startTime(OffsetDateTime.parse("2026-09-11T04:30:00Z"))
                .endTime(OffsetDateTime.parse("2026-09-11T05:30:00Z"))
                .reason("First Valid Request")
                .build();

        CreateBookingRequest request2 = CreateBookingRequest.builder()
                .roomId(testRoom.getId())
                .startTime(OffsetDateTime.parse("2026-09-11T06:30:00Z"))
                .endTime(OffsetDateTime.parse("2026-09-11T07:30:00Z"))
                .reason("Second Conflicting Request")
                .build();

        // First request succeeds
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + userToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // Second request with SAME key but DIFFERENT payload returns 409 IDEMPOTENCY_CONFLICT
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + userToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_CONFLICT"));

        // Only 1 booking was created
        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("D. Operation Recovery: GET /api/operations/{operationId} returns authoritative status; User B cannot access User A's operation (403)")
    void testOperationRecoveryAndOwnership() throws Exception {
        String idempotencyKey = "RECOVERY-KEY-1";

        CreateBookingRequest request = CreateBookingRequest.builder()
                .roomId(testRoom.getId())
                .startTime(OffsetDateTime.parse("2026-09-12T04:30:00Z"))
                .endTime(OffsetDateTime.parse("2026-09-12T05:30:00Z"))
                .reason("Recovery Test")
                .build();

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + userToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        var op = agentOperationRepository.findAll().get(0);
        String operationId = op.getOperationId();

        // User A reads their own operation -> 200 OK
        mockMvc.perform(get("/api/operations/" + operationId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value(operationId))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        // User B attempts to access User A's operation -> 403 FORBIDDEN
        mockMvc.perform(get("/api/operations/" + operationId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }
}
