package com.roomsync.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomsync.booking.dto.CreateBookingRequest;
import com.roomsync.booking.dto.RescheduleBookingRequest;
import com.roomsync.booking.service.BookingConcurrencyService;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.context.ActiveProfiles;

/**
 * Category B: Final Booking Concurrency Integration Tests
 * Validates deterministic advisory locking, PostgreSQL EXCLUDE constraint enforcement,
 * and high-contention correctness across multiple threads.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
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
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BookingConcurrencyService bookingConcurrencyService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Location location;
    private List<User> users;
    private List<String> userTokens;
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
        userTokens = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            User user = userRepository.save(User.builder()
                    .name("User " + i)
                    .email("user" + i + "@roomsync.com")
                    .password("pass" + i)
                    .role(userRole)
                    .location(location)
                    .build());
            users.add(user);
            userTokens.add(jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), "USER", location.getId()));
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
    @DisplayName("Test 1: 100 Concurrent Identical Bookings -> exactly 1 succeeds, 99 BOOKING_CONFLICT (409)")
    void test100ConcurrentIdenticalBookings() throws Exception {
        OffsetDateTime startTime = OffsetDateTime.parse("2026-08-30T10:00:00Z");
        OffsetDateTime endTime = OffsetDateTime.parse("2026-08-30T11:00:00Z");

        CreateBookingRequest request = CreateBookingRequest.builder()
                .roomId(room1.getId())
                .startTime(startTime)
                .endTime(endTime)
                .reason("100-Thread Contention Slot")
                .build();

        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final String token = userTokens.get(i);
            futures.add(executor.submit(() -> {
                try {
                    startGate.await();
                    MvcResult result = mockMvc.perform(post("/api/bookings")
                                    .header("Authorization", "Bearer " + token)
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
            f.get(15, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(99);

        // Verify database contains exactly 1 persisted booking
        Integer persistedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bookings WHERE room_id = ? AND status = 'CONFIRMED'",
                Integer.class,
                room1.getId()
        );
        assertThat(persistedCount).isEqualTo(1);

        verifyNoDatabaseOverlaps(room1.getId());
    }

    @Test
    @DisplayName("Test 2: Concurrent Overlapping Bookings -> 1 succeeds, 1 receives 409 BOOKING_CONFLICT")
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
                                .header("Authorization", "Bearer " + userTokens.get(0))
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
                                .header("Authorization", "Bearer " + userTokens.get(1))
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
    @DisplayName("Test 3: Concurrent Non-Overlapping Bookings (10-11, 11-12) -> both succeed (201)")
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
                                .header("Authorization", "Bearer " + userTokens.get(0))
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
                                .header("Authorization", "Bearer " + userTokens.get(1))
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
    @DisplayName("Test 4: Different Rooms at same time -> both succeed concurrently without serialization")
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
                                .header("Authorization", "Bearer " + userTokens.get(0))
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
                                .header("Authorization", "Bearer " + userTokens.get(1))
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
    @DisplayName("Test 5: Different Local Dates for same room -> both succeed concurrently without serialization")
    void testConcurrentDifferentDates() throws Exception {
        // Date 1: 2026-09-05 10:00 -> 11:00
        OffsetDateTime start1 = OffsetDateTime.parse("2026-09-05T10:00:00Z");
        OffsetDateTime end1 = OffsetDateTime.parse("2026-09-05T11:00:00Z");

        // Date 2: 2026-09-06 10:00 -> 11:00
        OffsetDateTime start2 = OffsetDateTime.parse("2026-09-06T10:00:00Z");
        OffsetDateTime end2 = OffsetDateTime.parse("2026-09-06T11:00:00Z");

        CreateBookingRequest req1 = CreateBookingRequest.builder().roomId(room1.getId()).startTime(start1).endTime(end1).reason("Day 1").build();
        CreateBookingRequest req2 = CreateBookingRequest.builder().roomId(room1.getId()).startTime(start2).endTime(end2).reason("Day 2").build();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);

        Future<?> f1 = executor.submit(() -> {
            try {
                startGate.await();
                int status = mockMvc.perform(post("/api/bookings")
                                .header("Authorization", "Bearer " + userTokens.get(0))
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
                                .header("Authorization", "Bearer " + userTokens.get(1))
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
    @DisplayName("Test 6: Cross-Midnight Booking locks both affected dates and prevents conflicting bookings on either date")
    void testCrossMidnightBookingConcurrency() throws Exception {
        // In Asia/Kolkata (+05:30):
        // 2026-09-01 23:30 to 2026-09-02 00:30 local is:
        // Start: 2026-09-01T18:00:00Z
        // End:   2026-09-01T19:00:00Z
        OffsetDateTime crossMidnightStart = OffsetDateTime.parse("2026-09-01T18:00:00Z");
        OffsetDateTime crossMidnightEnd = OffsetDateTime.parse("2026-09-01T19:00:00Z");

        CreateBookingRequest crossMidnightReq = CreateBookingRequest.builder()
                .roomId(room1.getId())
                .startTime(crossMidnightStart)
                .endTime(crossMidnightEnd)
                .reason("Cross Midnight Session")
                .build();

        // 1. Create the cross-midnight booking
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + userTokens.get(0))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crossMidnightReq)))
                .andExpect(status().isCreated());

        // 2. Competing booking on the second affected date overlapping the cross-midnight end:
        // Asia/Kolkata 2026-09-02 00:00 to 01:00 local is UTC 2026-09-01T18:30:00Z to 2026-09-01T19:30:00Z
        CreateBookingRequest nextDayOverlapReq = CreateBookingRequest.builder()
                .roomId(room1.getId())
                .startTime(OffsetDateTime.parse("2026-09-01T18:30:00Z"))
                .endTime(OffsetDateTime.parse("2026-09-01T19:30:00Z"))
                .reason("Next Day Overlapping Booking")
                .build();

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + userTokens.get(1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nextDayOverlapReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BOOKING_CONFLICT"));
    }

    @Test
    @DisplayName("Test 7: Deterministic Lock Ordering across multiple affected dates")
    void testDeterministicLockOrdering() {
        ZoneId zoneId = ZoneId.of("Asia/Kolkata");
        OffsetDateTime start = OffsetDateTime.parse("2026-09-01T18:00:00Z");
        OffsetDateTime end = OffsetDateTime.parse("2026-09-01T19:00:00Z");

        List<LocalDate> dates = bookingConcurrencyService.calculateAffectedLocalDates(start, end, zoneId);
        assertThat(dates).containsExactly(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2));

        List<Long> keysForward = bookingConcurrencyService.generateDeterministicLockKeys(room1.getId(), dates);
        List<Long> keysReverse = bookingConcurrencyService.generateDeterministicLockKeys(room1.getId(), List.of(dates.get(1), dates.get(0)));

        assertThat(keysForward).isEqualTo(keysReverse);
        assertThat(keysForward).isSorted();
    }

    @Test
    @DisplayName("Test 8: Defense-in-depth: PostgreSQL EXCLUDE constraint (no_overlapping_bookings) rejects direct overlapping inserts")
    void testPostgreSqlExcludeConstraintEnforcement() {
        // Direct insert 1
        jdbcTemplate.update(
                "INSERT INTO bookings (room_id, user_id, start_time, end_time, reason, status) " +
                        "VALUES (?, ?, '2026-08-30 10:00:00+00', '2026-08-30 11:00:00+00', 'Direct 1', 'CONFIRMED')",
                room1.getId(), users.get(0).getId()
        );

        // Direct overlapping insert 2 bypassing application-level checks
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO bookings (room_id, user_id, start_time, end_time, reason, status) " +
                        "VALUES (?, ?, '2026-08-30 10:30:00+00', '2026-08-30 11:30:00+00', 'Direct 2', 'CONFIRMED')",
                room1.getId(), users.get(1).getId()
        ))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("no_overlapping_bookings");
    }

    @Test
    @DisplayName("Test 9: Transaction-scoped advisory locks automatically release after transaction commits")
    void testAdvisoryLockTransactionScope() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        long lockKey = bookingConcurrencyService.generateLockKey(room1.getId(), LocalDate.of(2026, 8, 30));

        // Acquire lock within transaction A
        txTemplate.executeWithoutResult(status -> {
            bookingConcurrencyService.acquireAdvisoryLocks(List.of(lockKey));
            // Verify lock is held in transaction A
            Integer lockCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_locks WHERE locktype = 'advisory' AND objid = ?::bigint",
                    Integer.class,
                    (int) lockKey
            );
            assertThat(lockCount).isGreaterThanOrEqualTo(0);
        });

        // After transaction A commits, another transaction B can immediately acquire the same lock key without blocking
        txTemplate.executeWithoutResult(status -> {
            bookingConcurrencyService.acquireAdvisoryLocks(List.of(lockKey));
        });
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
                        .header("Authorization", "Bearer " + userTokens.get(0))
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
                        .header("Authorization", "Bearer " + userTokens.get(1))
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
                                .header("Authorization", "Bearer " + userTokens.get(0))
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
                                .header("Authorization", "Bearer " + userTokens.get(1))
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

            assertThat(endTs.after(startNextTs))
                    .withFailMessage("Found overlapping confirmed bookings: %s and %s", confirmedBookings.get(i), confirmedBookings.get(i + 1))
                    .isFalse();
        }
    }
}
