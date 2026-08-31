package com.roomsync.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomsync.booking.dto.BookingResponse;
import com.roomsync.booking.dto.CancelBookingRequest;
import com.roomsync.booking.dto.CreateBookingRequest;
import com.roomsync.booking.dto.RescheduleBookingRequest;
import com.roomsync.booking.entity.Booking;
import com.roomsync.booking.entity.BookingStatus;
import com.roomsync.booking.repository.BookingRepository;
import com.roomsync.booking.scheduler.BookingCompletionScheduler;
import com.roomsync.booking.service.BookingService;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingLifecycleIntegrationTest {

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
    private BookingRepository bookingRepository;

    @Autowired
    private com.roomsync.booking.repository.BookingSeriesRepository bookingSeriesRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingCompletionScheduler bookingCompletionScheduler;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Location locationMumbai;
    private User userAlice;
    private User userBob;
    private User adminUser;
    private Room roomAlpha;
    private Room roomBeta;
    private String aliceToken;
    private String bobToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE bookings, rooms, users, locations RESTART IDENTITY CASCADE");

        locationMumbai = locationRepository.save(Location.builder()
                .name("Mumbai HQ")
                .code("MUM")
                .active(true)
                .timezone("Asia/Kolkata")
                .build());

        Role userRole = roleRepository.findByName("USER").orElseGet(() ->
                roleRepository.save(Role.builder().name("USER").build()));
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() ->
                roleRepository.save(Role.builder().name("ADMIN").build()));

        userAlice = userRepository.save(User.builder()
                .wissenId("WT1091")
                .name("Alice")
                .email("alice@roomsync.com")
                .password("hash1")
                .role(userRole)
                .location(locationMumbai)
                .build());

        userBob = userRepository.save(User.builder()
                .wissenId("WT1092")
                .name("Bob")
                .email("bob@roomsync.com")
                .password("hash2")
                .role(userRole)
                .location(locationMumbai)
                .build());

        adminUser = userRepository.save(User.builder()
                .wissenId("WT1093")
                .name("Admin")
                .email("admin@roomsync.com")
                .password("hash3")
                .role(adminRole)
                .location(locationMumbai)
                .build());

        roomAlpha = roomRepository.save(Room.builder()
                .location(locationMumbai)
                .name("Room Alpha")
                .capacity(10)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build());

        roomBeta = roomRepository.save(Room.builder()
                .location(locationMumbai)
                .name("Room Beta")
                .capacity(12)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build());

        aliceToken = jwtTokenProvider.generateAccessToken(userAlice.getId(), userAlice.getWissenId(), userAlice.getEmail(), "USER", locationMumbai.getId());
        bobToken = jwtTokenProvider.generateAccessToken(userBob.getId(), userBob.getWissenId(), userBob.getEmail(), "USER", locationMumbai.getId());
        adminToken = jwtTokenProvider.generateAccessToken(adminUser.getId(), adminUser.getWissenId(), adminUser.getEmail(), "ADMIN", locationMumbai.getId());
    }

    @Nested
    @DisplayName("Create Booking Lifecycle Tests")
    class CreateTests {

        @Test
        @DisplayName("Valid create produces CONFIRMED booking")
        void testCreateBookingConfirmed() throws Exception {
            OffsetDateTime start = OffsetDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
            OffsetDateTime end = start.plusHours(1);

            CreateBookingRequest request = CreateBookingRequest.builder()
                    .roomId(roomAlpha.getId())
                    .startTime(start)
                    .endTime(end)
                    .reason("Sprint Kickoff")
                    .build();

            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + aliceToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.roomId").value(roomAlpha.getId()))
                    .andExpect(jsonPath("$.userId").value(userAlice.getId()));
        }

        @Test
        @DisplayName("Overlapping create returns 409 BOOKING_CONFLICT")
        void testCreateBookingOverlap() throws Exception {
            OffsetDateTime start = OffsetDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
            OffsetDateTime end = start.plusHours(1);

            CreateBookingRequest req1 = CreateBookingRequest.builder()
                    .roomId(roomAlpha.getId())
                    .startTime(start)
                    .endTime(end)
                    .reason("Original Meeting")
                    .build();

            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + aliceToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req1)))
                    .andExpect(status().isCreated());

            CreateBookingRequest req2 = CreateBookingRequest.builder()
                    .roomId(roomAlpha.getId())
                    .startTime(start.plusMinutes(15))
                    .endTime(end.plusMinutes(15))
                    .reason("Overlapping Request")
                    .build();

            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + bobToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req2)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("BOOKING_CONFLICT"));
        }
    }

    @Nested
    @DisplayName("Cancel Booking Lifecycle Tests")
    class CancelTests {

        @Test
        @DisplayName("Soft cancel sets CANCELLED, preserves times/row, and stores reason")
        void testSoftCancelWithReason() throws Exception {
            OffsetDateTime start = OffsetDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
            OffsetDateTime end = start.plusHours(1);

            CreateBookingRequest createReq = CreateBookingRequest.builder()
                    .roomId(roomAlpha.getId())
                    .startTime(start)
                    .endTime(end)
                    .reason("Product Demo")
                    .build();

            String respStr = mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + aliceToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createReq)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            Long bookingId = objectMapper.readTree(respStr).get("id").asLong();

            CancelBookingRequest cancelReq = CancelBookingRequest.builder()
                    .reason("Customer rescheduled demo")
                    .build();

            mockMvc.perform(delete("/api/bookings/" + bookingId)
                            .header("Authorization", "Bearer " + aliceToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(cancelReq)))
                    .andExpect(status().isNoContent());

            Booking dbBooking = bookingRepository.findById(bookingId).orElseThrow();
            assertThat(dbBooking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(dbBooking.getCancelledReason()).isEqualTo("Customer rescheduled demo");
            assertThat(dbBooking.getStartTime()).isEqualTo(start);
            assertThat(dbBooking.getEndTime()).isEqualTo(end);
            assertThat(dbBooking.getRoom().getId()).isEqualTo(roomAlpha.getId());
        }

        @Test
        @DisplayName("Reject cancellation of already cancelled booking")
        void testCancelAlreadyCancelled() throws Exception {
            OffsetDateTime start = OffsetDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
            OffsetDateTime end = start.plusHours(1);

            CreateBookingRequest createReq = CreateBookingRequest.builder()
                    .roomId(roomAlpha.getId())
                    .startTime(start)
                    .endTime(end)
                    .reason("Product Demo")
                    .build();

            String respStr = mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + aliceToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createReq)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            Long bookingId = objectMapper.readTree(respStr).get("id").asLong();

            // First cancel
            mockMvc.perform(delete("/api/bookings/" + bookingId)
                            .header("Authorization", "Bearer " + aliceToken))
                    .andExpect(status().isNoContent());

            // Second cancel -> 409
            mockMvc.perform(delete("/api/bookings/" + bookingId)
                            .header("Authorization", "Bearer " + aliceToken))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("BOOKING_CONFLICT"));
        }

        @Test
        @DisplayName("Reject cancellation by non-owner USER (403 FORBIDDEN)")
        void testCancelNonOwnerForbidden() throws Exception {
            OffsetDateTime start = OffsetDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
            OffsetDateTime end = start.plusHours(1);

            CreateBookingRequest createReq = CreateBookingRequest.builder()
                    .roomId(roomAlpha.getId())
                    .startTime(start)
                    .endTime(end)
                    .reason("Confidential Review")
                    .build();

            String respStr = mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + aliceToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createReq)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            Long bookingId = objectMapper.readTree(respStr).get("id").asLong();

            mockMvc.perform(delete("/api/bookings/" + bookingId)
                            .header("Authorization", "Bearer " + bobToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("Allow cancellation by ADMIN user")
        void testCancelByAdmin() throws Exception {
            OffsetDateTime start = OffsetDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
            OffsetDateTime end = start.plusHours(1);

            CreateBookingRequest createReq = CreateBookingRequest.builder()
                    .roomId(roomAlpha.getId())
                    .startTime(start)
                    .endTime(end)
                    .reason("Board Meeting")
                    .build();

            String respStr = mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + aliceToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createReq)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            Long bookingId = objectMapper.readTree(respStr).get("id").asLong();

            mockMvc.perform(delete("/api/bookings/" + bookingId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNoContent());

            Booking dbBooking = bookingRepository.findById(bookingId).orElseThrow();
            assertThat(dbBooking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(dbBooking.getCancelledReason()).isEqualTo("Cancelled by admin");
        }
    }

    @Nested
    @DisplayName("Reschedule Booking Lifecycle Tests")
    class RescheduleTests {

        @Test
        @DisplayName("Reschedule cancels original with RESCHEDULED reason and creates linked CONFIRMED replacement")
        void testRescheduleSuccessAndLineage() throws Exception {
            OffsetDateTime originalStart = OffsetDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
            OffsetDateTime originalEnd = originalStart.plusHours(1);

            CreateBookingRequest createReq = CreateBookingRequest.builder()
                    .roomId(roomAlpha.getId())
                    .startTime(originalStart)
                    .endTime(originalEnd)
                    .reason("Architecture Brainstorming")
                    .build();

            String createResp = mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + aliceToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createReq)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            Long originalId = objectMapper.readTree(createResp).get("id").asLong();

            OffsetDateTime newStart = originalStart.withHour(14);
            OffsetDateTime newEnd = originalStart.withHour(15);
            RescheduleBookingRequest reschedReq = RescheduleBookingRequest.builder()
                    .startTime(newStart)
                    .endTime(newEnd)
                    .build();

            String reschedResp = mockMvc.perform(put("/api/bookings/" + originalId)
                            .header("Authorization", "Bearer " + aliceToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reschedReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.rescheduledFromId").value(originalId))
                    .andReturn().getResponse().getContentAsString();

            Long replacementId = objectMapper.readTree(reschedResp).get("id").asLong();
            assertThat(replacementId).isNotEqualTo(originalId);

            // Verify original booking is CANCELLED with reason RESCHEDULED
            Booking originalDb = bookingRepository.findById(originalId).orElseThrow();
            assertThat(originalDb.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(originalDb.getCancelledReason()).isEqualTo("RESCHEDULED");
            assertThat(originalDb.getStartTime()).isEqualTo(originalStart);
            assertThat(originalDb.getEndTime()).isEqualTo(originalEnd);
            assertThat(originalDb.getRoom().getId()).isEqualTo(roomAlpha.getId());

            // Verify replacement booking is CONFIRMED with rescheduledFrom = original
            Booking replacementDb = bookingRepository.findById(replacementId).orElseThrow();
            assertThat(replacementDb.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(replacementDb.getRescheduledFrom().getId()).isEqualTo(originalId);
            assertThat(replacementDb.getStartTime()).isEqualTo(newStart);
            assertThat(replacementDb.getEndTime()).isEqualTo(newEnd);
        }

        @Test
        @DisplayName("Reschedule to a different room locks both rooms and links replacement")
        void testRescheduleToDifferentRoom() throws Exception {
            OffsetDateTime originalStart = OffsetDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
            OffsetDateTime originalEnd = originalStart.plusHours(1);

            CreateBookingRequest createReq = CreateBookingRequest.builder()
                    .roomId(roomAlpha.getId())
                    .startTime(originalStart)
                    .endTime(originalEnd)
                    .reason("Room Alpha Meeting")
                    .build();

            String createResp = mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + aliceToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createReq)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            Long originalId = objectMapper.readTree(createResp).get("id").asLong();

            OffsetDateTime newStart = originalStart.withHour(11);
            OffsetDateTime newEnd = originalStart.withHour(12);
            RescheduleBookingRequest reschedReq = RescheduleBookingRequest.builder()
                    .roomId(roomBeta.getId())
                    .startTime(newStart)
                    .endTime(newEnd)
                    .build();

            mockMvc.perform(put("/api/bookings/" + originalId)
                            .header("Authorization", "Bearer " + aliceToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reschedReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.roomId").value(roomBeta.getId()))
                    .andExpect(jsonPath("$.rescheduledFromId").value(originalId));
        }

        @Test
        @DisplayName("Atomicity: If replacement booking conflicts, original remains CONFIRMED")
        void testRescheduleAtomicityOnOverlap() throws Exception {
            OffsetDateTime timeAStart = OffsetDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
            OffsetDateTime timeAEnd = timeAStart.plusHours(1);

            OffsetDateTime timeBStart = OffsetDateTime.now().plusDays(2).withHour(14).withMinute(0).withSecond(0).withNano(0);
            OffsetDateTime timeBEnd = timeBStart.plusHours(1);

            // Alice books 10:00 - 11:00
            CreateBookingRequest reqAlice = CreateBookingRequest.builder()
                    .roomId(roomAlpha.getId())
                    .startTime(timeAStart)
                    .endTime(timeAEnd)
                    .reason("Alice Slot")
                    .build();

            String aliceResp = mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + aliceToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reqAlice)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            Long aliceBookingId = objectMapper.readTree(aliceResp).get("id").asLong();

            // Bob books 14:00 - 15:00
            CreateBookingRequest reqBob = CreateBookingRequest.builder()
                    .roomId(roomAlpha.getId())
                    .startTime(timeBStart)
                    .endTime(timeBEnd)
                    .reason("Bob Slot")
                    .build();

            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + bobToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reqBob)))
                    .andExpect(status().isCreated());

            // Alice attempts to reschedule into Bob's 14:00 - 15:00 slot -> 409 Conflict
            RescheduleBookingRequest conflictResched = RescheduleBookingRequest.builder()
                    .startTime(timeBStart)
                    .endTime(timeBEnd)
                    .build();

            mockMvc.perform(put("/api/bookings/" + aliceBookingId)
                            .header("Authorization", "Bearer " + aliceToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(conflictResched)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("BOOKING_CONFLICT"));

            // Verify Alice's original booking remains CONFIRMED
            Booking aliceOriginal = bookingRepository.findById(aliceBookingId).orElseThrow();
            assertThat(aliceOriginal.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(aliceOriginal.getCancelledReason()).isNull();
        }

        @Test
        @DisplayName("TEST 3 & TEST 10: Recurring occurrence reschedule succeeds, soft-cancels original, retains series/occurrence index, and preserves DB invariant")
        void testRecurringOccurrenceRescheduleSuccessAndInvariant() throws Exception {
            // Create a recurring series
            com.roomsync.booking.entity.BookingSeries series = bookingSeriesRepository.save(com.roomsync.booking.entity.BookingSeries.builder()
                    .user(userAlice)
                    .seriesName("Sprint Planning")
                    .frequency(com.roomsync.booking.entity.RecurrenceFrequency.WEEKLY)
                    .startDate(java.time.LocalDate.now().plusDays(2))
                    .occurrenceCount(5)
                    .startLocalTime(java.time.LocalTime.of(10, 0))
                    .endLocalTime(java.time.LocalTime.of(11, 0))
                    .timezone("Asia/Kolkata")
                    .status(com.roomsync.booking.entity.BookingSeriesStatus.ACTIVE)
                    .build());

            OffsetDateTime originalStart = OffsetDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
            OffsetDateTime originalEnd = originalStart.plusHours(1);

            // Create initial confirmed occurrence #1
            Booking occurrence1 = bookingRepository.save(Booking.builder()
                    .room(roomAlpha)
                    .user(userAlice)
                    .series(series)
                    .occurrenceIndex(1)
                    .startTime(originalStart)
                    .endTime(originalEnd)
                    .reason("Sprint Planning #1")
                    .status(BookingStatus.CONFIRMED)
                    .build());

            Long originalId = occurrence1.getId();

            // Reschedule occurrence #1 to a new time
            OffsetDateTime newStart = originalStart.withHour(14);
            OffsetDateTime newEnd = originalStart.withHour(15);
            RescheduleBookingRequest reschedReq = RescheduleBookingRequest.builder()
                    .roomId(roomAlpha.getId())
                    .startTime(newStart)
                    .endTime(newEnd)
                    .build();

            String reschedResp = mockMvc.perform(put("/api/bookings/" + originalId)
                            .header("Authorization", "Bearer " + aliceToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reschedReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.rescheduledFromId").value(originalId))
                    .andExpect(jsonPath("$.seriesId").value(series.getId()))
                    .andExpect(jsonPath("$.occurrenceIndex").value(1))
                    .andReturn().getResponse().getContentAsString();

            Long replacementId = objectMapper.readTree(reschedResp).get("id").asLong();
            assertThat(replacementId).isNotEqualTo(originalId);

            // Verify original in DB is CANCELLED with reason RESCHEDULED and retains series info
            Booking dbOriginal = bookingRepository.findById(originalId).orElseThrow();
            assertThat(dbOriginal.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(dbOriginal.getCancelledReason()).isEqualTo("RESCHEDULED");
            assertThat(dbOriginal.getSeries().getId()).isEqualTo(series.getId());
            assertThat(dbOriginal.getOccurrenceIndex()).isEqualTo(1);

            // Verify replacement in DB is CONFIRMED, retains series info, and links to original
            Booking dbReplacement = bookingRepository.findById(replacementId).orElseThrow();
            assertThat(dbReplacement.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(dbReplacement.getRescheduledFrom().getId()).isEqualTo(originalId);
            assertThat(dbReplacement.getSeries().getId()).isEqualTo(series.getId());
            assertThat(dbReplacement.getOccurrenceIndex()).isEqualTo(1);
            assertThat(dbReplacement.getStartTime()).isEqualTo(newStart);
            assertThat(dbReplacement.getEndTime()).isEqualTo(newEnd);

            // Verify DB Invariant: At most ONE CONFIRMED booking exists for (series_id, occurrence_index)
            Integer duplicateConfirmedCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ( " +
                    "  SELECT series_id, occurrence_index, COUNT(*) " +
                    "  FROM bookings " +
                    "  WHERE status = 'CONFIRMED' AND series_id IS NOT NULL AND occurrence_index IS NOT NULL " +
                    "  GROUP BY series_id, occurrence_index HAVING COUNT(*) > 1 " +
                    ") dupes", Integer.class);
            assertThat(duplicateConfirmedCount).isEqualTo(0);
        }

        @Test
        @DisplayName("TEST 4: Reschedule replacement recurring booking again and verify lineage across multiple hops")
        void testRescheduleRecurringReplacementAgain() throws Exception {
            com.roomsync.booking.entity.BookingSeries series = bookingSeriesRepository.save(com.roomsync.booking.entity.BookingSeries.builder()
                    .user(userAlice)
                    .seriesName("Design Review")
                    .frequency(com.roomsync.booking.entity.RecurrenceFrequency.WEEKLY)
                    .startDate(java.time.LocalDate.now().plusDays(3))
                    .occurrenceCount(4)
                    .startLocalTime(java.time.LocalTime.of(11, 0))
                    .endLocalTime(java.time.LocalTime.of(12, 0))
                    .timezone("Asia/Kolkata")
                    .status(com.roomsync.booking.entity.BookingSeriesStatus.ACTIVE)
                    .build());

            OffsetDateTime start1 = OffsetDateTime.now().plusDays(3).withHour(11).withMinute(0).withSecond(0).withNano(0);
            OffsetDateTime end1 = start1.plusHours(1);

            Booking orig = bookingRepository.save(Booking.builder()
                    .room(roomAlpha)
                    .user(userAlice)
                    .series(series)
                    .occurrenceIndex(1)
                    .startTime(start1)
                    .endTime(end1)
                    .reason("Design Review #1")
                    .status(BookingStatus.CONFIRMED)
                    .build());

            // First reschedule: 11:00 -> 14:00
            OffsetDateTime start2 = start1.withHour(14);
            OffsetDateTime end2 = start1.withHour(15);
            String resp1 = mockMvc.perform(put("/api/bookings/" + orig.getId())
                            .header("Authorization", "Bearer " + aliceToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(RescheduleBookingRequest.builder()
                                    .startTime(start2).endTime(end2).build())))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            Long rep1Id = objectMapper.readTree(resp1).get("id").asLong();

            // Second reschedule: 14:00 -> 16:00
            OffsetDateTime start3 = start1.withHour(16);
            OffsetDateTime end3 = start1.withHour(17);
            String resp2 = mockMvc.perform(put("/api/bookings/" + rep1Id)
                            .header("Authorization", "Bearer " + aliceToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(RescheduleBookingRequest.builder()
                                    .startTime(start3).endTime(end3).build())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.rescheduledFromId").value(rep1Id))
                    .andExpect(jsonPath("$.seriesId").value(series.getId()))
                    .andExpect(jsonPath("$.occurrenceIndex").value(1))
                    .andReturn().getResponse().getContentAsString();
            Long rep2Id = objectMapper.readTree(resp2).get("id").asLong();

            // Verify all states:
            // Original: CANCELLED (RESCHEDULED)
            Booking dbOrig = bookingRepository.findById(orig.getId()).orElseThrow();
            assertThat(dbOrig.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(dbOrig.getCancelledReason()).isEqualTo("RESCHEDULED");

            // Rep1: CANCELLED (RESCHEDULED)
            Booking dbRep1 = bookingRepository.findById(rep1Id).orElseThrow();
            assertThat(dbRep1.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(dbRep1.getCancelledReason()).isEqualTo("RESCHEDULED");
            assertThat(dbRep1.getRescheduledFrom().getId()).isEqualTo(orig.getId());

            // Rep2: CONFIRMED
            Booking dbRep2 = bookingRepository.findById(rep2Id).orElseThrow();
            assertThat(dbRep2.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(dbRep2.getRescheduledFrom().getId()).isEqualTo(rep1Id);
            assertThat(dbRep2.getSeries().getId()).isEqualTo(series.getId());
            assertThat(dbRep2.getOccurrenceIndex()).isEqualTo(1);

            // Verify DB Invariant
            Integer duplicateConfirmedCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ( " +
                    "  SELECT series_id, occurrence_index, COUNT(*) " +
                    "  FROM bookings " +
                    "  WHERE status = 'CONFIRMED' AND series_id IS NOT NULL AND occurrence_index IS NOT NULL " +
                    "  GROUP BY series_id, occurrence_index HAVING COUNT(*) > 1 " +
                    ") dupes", Integer.class);
            assertThat(duplicateConfirmedCount).isEqualTo(0);
        }

        @Test
        @DisplayName("TEST 5 & TEST 6: Idempotent replay and Idempotency Conflict validation")
        void testRescheduleIdempotencyAndConflict() throws Exception {
            OffsetDateTime start = OffsetDateTime.now().plusDays(4).withHour(10).withMinute(0).withSecond(0).withNano(0);
            OffsetDateTime end = start.plusHours(1);

            Booking booking = bookingRepository.save(Booking.builder()
                    .room(roomAlpha)
                    .user(userAlice)
                    .startTime(start)
                    .endTime(end)
                    .reason("Idempotent Test Booking")
                    .status(BookingStatus.CONFIRMED)
                    .build());

            String idempotencyKey = java.util.UUID.randomUUID().toString();

            OffsetDateTime newStart = start.withHour(14);
            OffsetDateTime newEnd = start.withHour(15);
            RescheduleBookingRequest req = RescheduleBookingRequest.builder()
                    .startTime(newStart)
                    .endTime(newEnd)
                    .build();

            // First execution
            String resp1 = mockMvc.perform(put("/api/bookings/" + booking.getId() + "/reschedule")
                            .header("Authorization", "Bearer " + aliceToken)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andReturn().getResponse().getContentAsString();
            Long firstReplacementId = objectMapper.readTree(resp1).get("id").asLong();

            // Second execution (same key + same payload) -> Replays cached result
            String resp2 = mockMvc.perform(put("/api/bookings/" + booking.getId() + "/reschedule")
                            .header("Authorization", "Bearer " + aliceToken)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(firstReplacementId))
                    .andReturn().getResponse().getContentAsString();

            // Verify no additional booking was created
            long count = bookingRepository.count();
            assertThat(count).isEqualTo(2); // Original + exactly 1 replacement

            // Replay with same key but DIFFERENT payload -> 409 IDEMPOTENCY_CONFLICT
            RescheduleBookingRequest conflictPayload = RescheduleBookingRequest.builder()
                    .startTime(start.withHour(16))
                    .endTime(start.withHour(17))
                    .build();

            mockMvc.perform(put("/api/bookings/" + booking.getId() + "/reschedule")
                            .header("Authorization", "Bearer " + aliceToken)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(conflictPayload)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_CONFLICT"));
        }

        @Test
        @DisplayName("TEST 7: Reject rescheduling an already CANCELLED booking")
        void testRescheduleAlreadyCancelledBooking() throws Exception {
            OffsetDateTime start = OffsetDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
            OffsetDateTime end = start.plusHours(1);

            Booking cancelledBooking = bookingRepository.save(Booking.builder()
                    .room(roomAlpha)
                    .user(userAlice)
                    .startTime(start)
                    .endTime(end)
                    .reason("Cancelled Meeting")
                    .cancelledReason("No longer needed")
                    .status(BookingStatus.CANCELLED)
                    .build());

            RescheduleBookingRequest req = RescheduleBookingRequest.builder()
                    .startTime(start.withHour(14))
                    .endTime(start.withHour(15))
                    .build();

            mockMvc.perform(put("/api/bookings/" + cancelledBooking.getId() + "/reschedule")
                            .header("Authorization", "Bearer " + aliceToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("BOOKING_CONFLICT"));
        }

        @Test
        @DisplayName("TEST 8: Reject rescheduling a past / COMPLETED booking")
        void testRescheduleCompletedBooking() throws Exception {
            OffsetDateTime pastStart = OffsetDateTime.parse("2026-08-20T10:00:00Z");
            OffsetDateTime pastEnd = OffsetDateTime.parse("2026-08-20T11:00:00Z");

            Booking completedBooking = bookingRepository.save(Booking.builder()
                    .room(roomAlpha)
                    .user(userAlice)
                    .startTime(pastStart)
                    .endTime(pastEnd)
                    .reason("Past Architecture Review")
                    .status(BookingStatus.COMPLETED)
                    .build());

            OffsetDateTime futureStart = OffsetDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
            OffsetDateTime futureEnd = futureStart.plusHours(1);

            RescheduleBookingRequest req = RescheduleBookingRequest.builder()
                    .startTime(futureStart)
                    .endTime(futureEnd)
                    .build();

            mockMvc.perform(put("/api/bookings/" + completedBooking.getId() + "/reschedule")
                            .header("Authorization", "Bearer " + aliceToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("BOOKING_COMPLETED"));
        }
    }

    @Nested
    @DisplayName("Complete Booking and Scheduler Tests")
    class CompleteTests {

        @Test
        @DisplayName("Past confirmed bookings are completed; cancelled and future confirmed remain untouched")
        void testBookingCompletionScheduler() {
            OffsetDateTime pastStart = OffsetDateTime.parse("2026-08-20T10:00:00Z");
            OffsetDateTime pastEnd = OffsetDateTime.parse("2026-08-20T11:00:00Z");

            // Past Confirmed -> should become COMPLETED
            Booking pastConfirmed = bookingRepository.save(Booking.builder()
                    .room(roomAlpha)
                    .user(userAlice)
                    .startTime(pastStart)
                    .endTime(pastEnd)
                    .reason("Past Meeting 1")
                    .status(BookingStatus.CONFIRMED)
                    .build());

            // Past Cancelled -> should remain CANCELLED
            Booking pastCancelled = bookingRepository.save(Booking.builder()
                    .room(roomAlpha)
                    .user(userAlice)
                    .startTime(pastStart.plusHours(2))
                    .endTime(pastEnd.plusHours(2))
                    .reason("Past Meeting 2")
                    .status(BookingStatus.CANCELLED)
                    .cancelledReason("Cancelled by user")
                    .build());

            // Future Confirmed -> should remain CONFIRMED
            OffsetDateTime futureStart = OffsetDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
            OffsetDateTime futureEnd = futureStart.plusHours(1);
            Booking futureConfirmed = bookingRepository.save(Booking.builder()
                    .room(roomAlpha)
                    .user(userAlice)
                    .startTime(futureStart)
                    .endTime(futureEnd)
                    .reason("Future Meeting")
                    .status(BookingStatus.CONFIRMED)
                    .build());

            // Trigger scheduler job
            bookingCompletionScheduler.runBookingCompletion();

            Booking reloadedPastConfirmed = bookingRepository.findById(pastConfirmed.getId()).orElseThrow();
            assertThat(reloadedPastConfirmed.getStatus()).isEqualTo(BookingStatus.COMPLETED);

            Booking reloadedPastCancelled = bookingRepository.findById(pastCancelled.getId()).orElseThrow();
            assertThat(reloadedPastCancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);

            Booking reloadedFuture = bookingRepository.findById(futureConfirmed.getId()).orElseThrow();
            assertThat(reloadedFuture.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

            // Repeated execution is idempotent
            int secondRunUpdated = bookingService.completePastBookings();
            assertThat(secondRunUpdated).isEqualTo(0);
        }

        @Test
        @DisplayName("Reading past-end confirmed booking returns effective COMPLETED status")
        void testDynamicEffectiveStatusOnRead() throws Exception {
            OffsetDateTime pastStart = OffsetDateTime.parse("2026-08-20T10:00:00Z");
            OffsetDateTime pastEnd = OffsetDateTime.parse("2026-08-20T11:00:00Z");

            Booking pastConfirmed = bookingRepository.save(Booking.builder()
                    .room(roomAlpha)
                    .user(userAlice)
                    .startTime(pastStart)
                    .endTime(pastEnd)
                    .reason("Past Unprocessed Meeting")
                    .status(BookingStatus.CONFIRMED)
                    .build());

            mockMvc.perform(get("/api/bookings/" + pastConfirmed.getId())
                            .header("Authorization", "Bearer " + aliceToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }
    }
}
