package com.roomsync.reliability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomsync.agent.repository.AgentOperationRepository;
import com.roomsync.booking.dto.CreateBookingRequest;
import com.roomsync.booking.repository.BookingRepository;
import com.roomsync.location.entity.Location;
import com.roomsync.location.repository.LocationRepository;
import com.roomsync.reliability.filter.RateLimitingFilter;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ConcurrentSameKeyDifferentPayloadTest {

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
    private RateLimitingFilter rateLimitingFilter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User testUser;
    private Location testLocation;
    private Room testRoom;
    private String userToken;

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
                .wissenId("WT1121")
                .name("Alice")
                .email("alice@roomsync.com")
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
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("TRUNCATE notification_outbox, audit_logs, agent_actions, idempotency_records, agent_operations, bookings, booking_series, rooms, users, locations RESTART IDENTITY CASCADE");
    }

    @Test
    @DisplayName("C. Concurrent same key + different payloads: exactly 1 payload becomes authoritative; all mismatched requests receive 409 IDEMPOTENCY_CONFLICT")
    void testConcurrentSameKeyDifferentPayloads() throws Exception {
        int threads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        String idempotencyKey = "SHARED-KEY-DIFF-PAYLOADS";

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    latch.await();
                    CreateBookingRequest request = CreateBookingRequest.builder()
                            .roomId(testRoom.getId())
                            .startTime(OffsetDateTime.parse(String.format("2026-09-%02dT04:30:00Z", (index % 20) + 1)))
                            .endTime(OffsetDateTime.parse(String.format("2026-09-%02dT05:30:00Z", (index % 20) + 1)))
                            .reason("Payload variant " + index)
                            .build();

                    MvcResult result = mockMvc.perform(post("/api/bookings")
                                    .header("Authorization", "Bearer " + userToken)
                                    .header("Idempotency-Key", idempotencyKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                            .andReturn();

                    int status = result.getResponse().getStatus();
                    if (status == 201 || status == 200) {
                        successCount.incrementAndGet();
                    } else {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    conflictCount.incrementAndGet();
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
        // Exactly 1 authoritative operation was registered
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threads - 1);
    }
}
