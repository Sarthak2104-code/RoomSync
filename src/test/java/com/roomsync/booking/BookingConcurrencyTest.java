package com.roomsync.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomsync.booking.dto.CreateBookingRequest;
import com.roomsync.booking.dto.RescheduleBookingRequest;
import com.roomsync.location.entity.Location;
import com.roomsync.location.repository.LocationRepository;
import com.roomsync.room.entity.Room;
import com.roomsync.room.entity.RoomStatus;
import com.roomsync.room.repository.RoomRepository;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Category B: Concurrent Booking Integration Tests
 * Validates concurrency safety across multiple threads under real PostgreSQL transactions.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BookingConcurrencyTest {

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
    private JdbcTemplate jdbcTemplate;

    private Location location;
    private List<User> users;
    private Room room1;
    private Room room2;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE bookings, rooms, users, locations RESTART IDENTITY CASCADE");

        location = locationRepository.save(Location.builder()
                .name("Mumbai")
                .code("MUM")
                .active(true)
                .timezone("Asia/Kolkata")
                .build());

        Role userRole = roleRepository.findByName("USER").orElseGet(() ->
                roleRepository.save(Role.builder().name("USER").build()));

        users = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            users.add(userRepository.save(User.builder()
                    .name("User " + i)
                    .email("user" + i + "@roomsync.com")
                    .password("pass" + i)
                    .role(userRole)
                    .location(location)
                    .build()));
        }

        room1 = roomRepository.save(Room.builder()
                .location(location)
                .name("Grand Ballroom")
                .capacity(50)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build());

        room2 = roomRepository.save(Room.builder()
                .location(location)
                .name("Executive Boardroom")
                .capacity(20)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build());
    }

    @Test
    @DisplayName("Concurrent Test 1: Two users concurrently booking exact same slot -> exactly 1 succeeds, 1 receives 409")
    void testConcurrentExactOverlap() throws Exception {
        OffsetDateTime startTime = OffsetDateTime.parse("2026-08-30T10:00:00Z");
        OffsetDateTime endTime = OffsetDateTime.parse("2026-08-30T11:00:00Z");

        CreateBookingRequest request = CreateBookingRequest.builder()
                .roomId(room1.getId())
                .startTime(startTime)
                .endTime(endTime)
                .reason("Sprint Planning")
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            final Long userId = users.get(i).getId();
            futures.add(executor.submit(() -> {
                try {
                    startGate.await();
                    MvcResult result = mockMvc.perform(post("/api/bookings")
                                    .header("X-User-Id", userId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                            .andReturn();

                    int status = result.getResponse().getStatus();
                    if (status == 201) {
                        successCount.incrementAndGet();
                    } else if (status == 409) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        startGate.countDown();
        for (Future<?> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        verifyNoDatabaseOverlaps(room1.getId());
    }

    @Test
    @DisplayName("Concurrent Test 2: Two users with partial overlap -> exactly 1 succeeds, 1 receives 409")
    void testConcurrentPartialOverlap() throws Exception {
        OffsetDateTime startA = OffsetDateTime.parse("2026-08-30T10:00:00Z");
        OffsetDateTime endA = OffsetDateTime.parse("2026-08-30T11:00:00Z");

        OffsetDateTime startB = OffsetDateTime.parse("2026-08-30T10:30:00Z");
        OffsetDateTime endB = OffsetDateTime.parse("2026-08-30T11:30:00Z");

        CreateBookingRequest reqA = CreateBookingRequest.builder().roomId(room1.getId()).startTime(startA).endTime(endA).reason("Planning A").build();
        CreateBookingRequest reqB = CreateBookingRequest.builder().roomId(room1.getId()).startTime(startB).endTime(endB).reason("Planning B").build();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Future<?> fA = executor.submit(() -> {
            try {
                startGate.await();
                int status = mockMvc.perform(post("/api/bookings")
                                .header("X-User-Id", users.get(0).getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(reqA)))
                        .andReturn().getResponse().getStatus();
                if (status == 201) successCount.incrementAndGet();
                if (status == 409) conflictCount.incrementAndGet();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Future<?> fB = executor.submit(() -> {
            try {
                startGate.await();
                int status = mockMvc.perform(post("/api/bookings")
                                .header("X-User-Id", users.get(1).getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(reqB)))
                        .andReturn().getResponse().getStatus();
                if (status == 201) successCount.incrementAndGet();
                if (status == 409) conflictCount.incrementAndGet();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        startGate.countDown();
        fA.get(5, TimeUnit.SECONDS);
        fB.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        verifyNoDatabaseOverlaps(room1.getId());
    }

    @Test
    @DisplayName("Concurrent Test 3: Adjacent bookings (10-11, 11-12) -> both succeed (201)")
    void testConcurrentAdjacentBookings() throws Exception {
        OffsetDateTime startA = OffsetDateTime.parse("2026-08-30T10:00:00Z");
        OffsetDateTime endA = OffsetDateTime.parse("2026-08-30T11:00:00Z");

        OffsetDateTime startB = OffsetDateTime.parse("2026-08-30T11:00:00Z");
        OffsetDateTime endB = OffsetDateTime.parse("2026-08-30T12:00:00Z");

        CreateBookingRequest reqA = CreateBookingRequest.builder().roomId(room1.getId()).startTime(startA).endTime(endA).reason("Session A").build();
        CreateBookingRequest reqB = CreateBookingRequest.builder().roomId(room1.getId()).startTime(startB).endTime(endB).reason("Session B").build();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);

        Future<?> fA = executor.submit(() -> {
            try {
                startGate.await();
                int status = mockMvc.perform(post("/api/bookings")
                                .header("X-User-Id", users.get(0).getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(reqA)))
                        .andReturn().getResponse().getStatus();
                if (status == 201) successCount.incrementAndGet();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Future<?> fB = executor.submit(() -> {
            try {
                startGate.await();
                int status = mockMvc.perform(post("/api/bookings")
                                .header("X-User-Id", users.get(1).getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(reqB)))
                        .andReturn().getResponse().getStatus();
                if (status == 201) successCount.incrementAndGet();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        startGate.countDown();
        fA.get(5, TimeUnit.SECONDS);
        fB.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(2);

        verifyNoDatabaseOverlaps(room1.getId());
    }

    @Test
    @DisplayName("Concurrent Test 4: Different rooms at same time -> both succeed independently")
    void testConcurrentDifferentRooms() throws Exception {
        OffsetDateTime start = OffsetDateTime.parse("2026-08-30T10:00:00Z");
        OffsetDateTime end = OffsetDateTime.parse("2026-08-30T11:00:00Z");

        CreateBookingRequest req1 = CreateBookingRequest.builder().roomId(room1.getId()).startTime(start).endTime(end).reason("Room 1 Booking").build();
        CreateBookingRequest req2 = CreateBookingRequest.builder().roomId(room2.getId()).startTime(start).endTime(end).reason("Room 2 Booking").build();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);

        Future<?> f1 = executor.submit(() -> {
            try {
                startGate.await();
                int status = mockMvc.perform(post("/api/bookings")
                                .header("X-User-Id", users.get(0).getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req1)))
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
                                .header("X-User-Id", users.get(1).getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req2)))
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

    @Test
    @DisplayName("High Contention Test: 10 concurrent requests for same room slot -> exactly 1 success, 9 conflicts")
    void testHighContention10ConcurrentRequests() throws Exception {
        OffsetDateTime startTime = OffsetDateTime.parse("2026-08-30T14:00:00Z");
        OffsetDateTime endTime = OffsetDateTime.parse("2026-08-30T15:00:00Z");

        CreateBookingRequest request = CreateBookingRequest.builder()
                .roomId(room1.getId())
                .startTime(startTime)
                .endTime(endTime)
                .reason("Contention Test Slot")
                .build();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final Long userId = users.get(i).getId();
            futures.add(executor.submit(() -> {
                try {
                    startGate.await();
                    MvcResult result = mockMvc.perform(post("/api/bookings")
                                    .header("X-User-Id", userId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                            .andReturn();

                    int status = result.getResponse().getStatus();
                    statuses.add(status);
                    if (status == 201) {
                        successCount.incrementAndGet();
                    } else if (status == 409) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        startGate.countDown();
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(9);

        verifyNoDatabaseOverlaps(room1.getId());
    }

    @Test
    @DisplayName("Concurrent Reschedule Conflict: Two existing bookings rescheduled to overlapping slots -> 1 succeeds, 1 receives 409")
    void testConcurrentRescheduleConflict() throws Exception {
        // Create Booking A (10:00 -> 11:00)
        CreateBookingRequest reqA = CreateBookingRequest.builder()
                .roomId(room1.getId())
                .startTime(OffsetDateTime.parse("2026-08-30T10:00:00Z"))
                .endTime(OffsetDateTime.parse("2026-08-30T11:00:00Z"))
                .reason("Booking A")
                .build();
        String respA = mockMvc.perform(post("/api/bookings")
                        .header("X-User-Id", users.get(0).getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long bookingAId = objectMapper.readTree(respA).get("id").asLong();

        // Create Booking B (12:00 -> 13:00)
        CreateBookingRequest reqB = CreateBookingRequest.builder()
                .roomId(room1.getId())
                .startTime(OffsetDateTime.parse("2026-08-30T12:00:00Z"))
                .endTime(OffsetDateTime.parse("2026-08-30T13:00:00Z"))
                .reason("Booking B")
                .build();
        String respB = mockMvc.perform(post("/api/bookings")
                        .header("X-User-Id", users.get(1).getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long bookingBId = objectMapper.readTree(respB).get("id").asLong();

        // Both attempt to reschedule to overlapping slots (15:00 -> 16:00 vs 15:30 -> 16:30)
        RescheduleBookingRequest reschedA = RescheduleBookingRequest.builder()
                .startTime(OffsetDateTime.parse("2026-08-30T15:00:00Z"))
                .endTime(OffsetDateTime.parse("2026-08-30T16:00:00Z"))
                .build();

        RescheduleBookingRequest reschedB = RescheduleBookingRequest.builder()
                .startTime(OffsetDateTime.parse("2026-08-30T15:30:00Z"))
                .endTime(OffsetDateTime.parse("2026-08-30T16:30:00Z"))
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Future<?> fA = executor.submit(() -> {
            try {
                startGate.await();
                int status = mockMvc.perform(put("/api/bookings/" + bookingAId)
                                .header("X-User-Id", users.get(0).getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(reschedA)))
                        .andReturn().getResponse().getStatus();
                if (status == 200) successCount.incrementAndGet();
                if (status == 409) conflictCount.incrementAndGet();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Future<?> fB = executor.submit(() -> {
            try {
                startGate.await();
                int status = mockMvc.perform(put("/api/bookings/" + bookingBId)
                                .header("X-User-Id", users.get(1).getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(reschedB)))
                        .andReturn().getResponse().getStatus();
                if (status == 200) successCount.incrementAndGet();
                if (status == 409) conflictCount.incrementAndGet();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        startGate.countDown();
        fA.get(5, TimeUnit.SECONDS);
        fB.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        verifyNoDatabaseOverlaps(room1.getId());
    }

    /**
     * Helper verification: query PostgreSQL directly and verify zero overlapping CONFIRMED bookings exist.
     */
    private void verifyNoDatabaseOverlaps(Long roomId) {
        List<Map<String, Object>> confirmedBookings = jdbcTemplate.queryForList(
                "SELECT id, start_time, end_time FROM bookings WHERE room_id = ? AND status = 'CONFIRMED' ORDER BY start_time",
                roomId
        );

        for (int i = 0; i < confirmedBookings.size() - 1; i++) {
            java.sql.Timestamp endTs = (java.sql.Timestamp) confirmedBookings.get(i).get("end_time");
            java.sql.Timestamp startNextTs = (java.sql.Timestamp) confirmedBookings.get(i + 1).get("start_time");

            // Adjacent is allowed (endTs.equals(startNextTs)), but endTs must NOT be after startNextTs
            assertThat(endTs.after(startNextTs))
                    .withFailMessage("Found overlapping confirmed bookings: %s and %s", confirmedBookings.get(i), confirmedBookings.get(i + 1))
                    .isFalse();
        }
    }
}
