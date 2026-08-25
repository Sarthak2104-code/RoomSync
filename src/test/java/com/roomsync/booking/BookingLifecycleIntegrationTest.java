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
                .name("Alice")
                .email("alice@roomsync.com")
                .password("hash1")
                .role(userRole)
                .location(locationMumbai)
                .build());

        userBob = userRepository.save(User.builder()
                .name("Bob")
                .email("bob@roomsync.com")
                .password("hash2")
                .role(userRole)
                .location(locationMumbai)
                .build());

        adminUser = userRepository.save(User.builder()
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

        aliceToken = jwtTokenProvider.generateAccessToken(userAlice.getId(), userAlice.getEmail(), "USER", locationMumbai.getId());
        bobToken = jwtTokenProvider.generateAccessToken(userBob.getId(), userBob.getEmail(), "USER", locationMumbai.getId());
        adminToken = jwtTokenProvider.generateAccessToken(adminUser.getId(), adminUser.getEmail(), "ADMIN", locationMumbai.getId());
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
