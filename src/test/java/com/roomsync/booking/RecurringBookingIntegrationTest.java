package com.roomsync.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomsync.admin.entity.AdminRequest;
import com.roomsync.admin.repository.AdminRequestRepository;
import com.roomsync.booking.dto.BookingResponse;
import com.roomsync.booking.dto.ContactAdminOccurrenceRequest;
import com.roomsync.booking.dto.CreateBookingRequest;
import com.roomsync.booking.dto.CreateRecurringBookingRequest;
import com.roomsync.booking.dto.RecurringConfirmationResponse;
import com.roomsync.booking.dto.RecurringPreviewResponse;
import com.roomsync.booking.dto.ResolveAlternateRoomRequest;
import com.roomsync.booking.entity.Booking;
import com.roomsync.booking.entity.BookingSeries;
import com.roomsync.booking.entity.BookingSeriesStatus;
import com.roomsync.booking.entity.BookingStatus;
import com.roomsync.booking.entity.RecurrenceFrequency;
import com.roomsync.booking.repository.BookingRepository;
import com.roomsync.booking.repository.BookingSeriesRepository;
import com.roomsync.common.time.TimeService;
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
import org.junit.jupiter.api.AfterEach;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecurringBookingIntegrationTest {

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
    private BookingSeriesRepository bookingSeriesRepository;

    @Autowired
    private AdminRequestRepository adminRequestRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Location locationMumbai;
    private User userAlice;
    private User userBob;
    private Room roomAlpha;
    private Room roomBeta;
    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE admin_requests, bookings, booking_series, rooms, users, locations RESTART IDENTITY CASCADE");

        locationMumbai = locationRepository.save(Location.builder()
                .name("Mumbai HQ")
                .code("MUM")
                .active(true)
                .timezone("Asia/Kolkata")
                .build());

        Role userRole = roleRepository.findByName("USER").orElseGet(() ->
                roleRepository.save(Role.builder().name("USER").build()));

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
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("TRUNCATE admin_requests, bookings, booking_series, rooms, users, locations RESTART IDENTITY CASCADE");
    }


    @Test
    @DisplayName("Critical Checkpoint: 13 occurrences with 3 conflicts -> 10 confirmed, 3 unresolved, series PARTIALLY_CONFIRMED")
    void test13OccurrencesPartialSuccessScenario() throws Exception {
        LocalDate startDate = LocalDate.now().plusDays(2);
        LocalTime startTime = LocalTime.of(10, 0);
        LocalTime endTime = LocalTime.of(11, 0);

        // Pre-book conflicting slots on Occurrences 3, 7, and 11
        for (int occ : List.of(3, 7, 11)) {
            LocalDate conflictDate = startDate.plusDays(occ - 1);
            // 10:00 to 11:00 IST -> 04:30 to 05:30 UTC
            OffsetDateTime startUtc = conflictDate.atTime(startTime).atZone(ZoneId.of("Asia/Kolkata")).toOffsetDateTime();
            OffsetDateTime endUtc = conflictDate.atTime(endTime).atZone(ZoneId.of("Asia/Kolkata")).toOffsetDateTime();

            bookingRepository.save(Booking.builder()
                    .room(roomAlpha)
                    .user(userBob)
                    .startTime(startUtc)
                    .endTime(endUtc)
                    .reason("Pre-existing conflict on occ " + occ)
                    .status(BookingStatus.CONFIRMED)
                    .build());
        }

        CreateRecurringBookingRequest request = CreateRecurringBookingRequest.builder()
                .roomId(roomAlpha.getId())
                .seriesName("Daily Standup Series")
                .frequency(RecurrenceFrequency.DAILY)
                .startDate(startDate)
                .occurrenceCount(13)
                .startLocalTime(startTime)
                .endLocalTime(endTime)
                .reason("Sprint Standup")
                .build();

        // 1. Preview API -> 10 available, 3 conflict
        mockMvc.perform(post("/api/bookings/recurring/preview")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOccurrences").value(13))
                .andExpect(jsonPath("$.availableCount").value(10))
                .andExpect(jsonPath("$.conflictCount").value(3));

        // 2. Confirm API -> Creates 10 bookings, marks series PARTIALLY_CONFIRMED
        String respStr = mockMvc.perform(post("/api/bookings/recurring")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalOccurrences").value(13))
                .andExpect(jsonPath("$.confirmedCount").value(10))
                .andExpect(jsonPath("$.conflictCount").value(3))
                .andExpect(jsonPath("$.seriesStatus").value("PARTIALLY_CONFIRMED"))
                .andReturn().getResponse().getContentAsString();

        RecurringConfirmationResponse confirmation = objectMapper.readValue(respStr, RecurringConfirmationResponse.class);
        Long seriesId = confirmation.getSeriesId();

        // Verify Series entity in DB
        BookingSeries seriesDb = bookingSeriesRepository.findById(seriesId).orElseThrow();
        assertThat(seriesDb.getStatus()).isEqualTo(BookingSeriesStatus.PARTIALLY_CONFIRMED);

        // Verify exactly 10 CONFIRMED bookings belong to this series in DB
        List<Booking> seriesBookings = bookingRepository.findAllBySeriesIdOrderByOccurrenceIndexAsc(seriesId);
        assertThat(seriesBookings).hasSize(10);
        for (Booking b : seriesBookings) {
            assertThat(b.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(b.getSeries().getId()).isEqualTo(seriesId);
            assertThat(b.getOccurrenceIndex()).isNotIn(3, 7, 11);
        }
    }

    @Test
    @DisplayName("Transaction Isolation: Occurrence 1 succeeds, 2 conflicts, 3 succeeds -> 1 and 3 persisted")
    void testTransactionIsolationBetweenOccurrences() throws Exception {
        LocalDate startDate = LocalDate.now().plusDays(2);
        LocalTime startTime = LocalTime.of(14, 0);
        LocalTime endTime = LocalTime.of(15, 0);

        // Create conflict on Occurrence 2
        LocalDate conflictDate = startDate.plusDays(1);
        OffsetDateTime startUtc = conflictDate.atTime(startTime).atZone(ZoneId.of("Asia/Kolkata")).toOffsetDateTime();
        OffsetDateTime endUtc = conflictDate.atTime(endTime).atZone(ZoneId.of("Asia/Kolkata")).toOffsetDateTime();
        bookingRepository.save(Booking.builder()
                .room(roomAlpha)
                .user(userBob)
                .startTime(startUtc)
                .endTime(endUtc)
                .reason("Pre-existing conflict")
                .status(BookingStatus.CONFIRMED)
                .build());

        CreateRecurringBookingRequest request = CreateRecurringBookingRequest.builder()
                .roomId(roomAlpha.getId())
                .seriesName("Isolated 3-Day Series")
                .frequency(RecurrenceFrequency.DAILY)
                .startDate(startDate)
                .occurrenceCount(3)
                .startLocalTime(startTime)
                .endLocalTime(endTime)
                .reason("Isolation Test")
                .build();

        String respStr = mockMvc.perform(post("/api/bookings/recurring")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.confirmedCount").value(2))
                .andExpect(jsonPath("$.conflictCount").value(1))
                .andExpect(jsonPath("$.seriesStatus").value("PARTIALLY_CONFIRMED"))
                .andReturn().getResponse().getContentAsString();

        RecurringConfirmationResponse confirmation = objectMapper.readValue(respStr, RecurringConfirmationResponse.class);
        Long seriesId = confirmation.getSeriesId();

        // Occurrences 1 and 3 must exist in DB
        Booking occ1 = bookingRepository.findBySeriesIdAndOccurrenceIndex(seriesId, 1).orElseThrow();
        assertThat(occ1.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        Booking occ3 = bookingRepository.findBySeriesIdAndOccurrenceIndex(seriesId, 3).orElseThrow();
        assertThat(occ3.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        // Occurrence 2 must NOT have a booking created
        assertThat(bookingRepository.findBySeriesIdAndOccurrenceIndex(seriesId, 2)).isEmpty();
    }

    @Test
    @DisplayName("Cancel Occurrence: Soft cancels individual booking while series remains intact")
    void testCancelIndividualOccurrence() throws Exception {
        LocalDate startDate = LocalDate.now().plusDays(2);

        CreateRecurringBookingRequest request = CreateRecurringBookingRequest.builder()
                .roomId(roomAlpha.getId())
                .seriesName("Standup Series")
                .frequency(RecurrenceFrequency.DAILY)
                .startDate(startDate)
                .occurrenceCount(3)
                .startLocalTime(LocalTime.of(16, 0))
                .endLocalTime(LocalTime.of(17, 0))
                .reason("Daily Review")
                .build();

        String respStr = mockMvc.perform(post("/api/bookings/recurring")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long seriesId = objectMapper.readTree(respStr).get("seriesId").asLong();

        // Cancel occurrence 2
        mockMvc.perform(delete("/api/bookings/recurring/" + seriesId + "/occurrences/2?reason=Holiday")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isNoContent());

        // Occurrence 2 is CANCELLED with reason Holiday
        Booking occ2 = bookingRepository.findBySeriesIdAndOccurrenceIndex(seriesId, 2).orElseThrow();
        assertThat(occ2.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(occ2.getCancelledReason()).isEqualTo("Holiday");

        // Occurrence 1 and 3 remain CONFIRMED
        Booking occ1 = bookingRepository.findBySeriesIdAndOccurrenceIndex(seriesId, 1).orElseThrow();
        assertThat(occ1.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        // Series still exists in DB
        BookingSeries series = bookingSeriesRepository.findById(seriesId).orElseThrow();
        assertThat(series.getStatus()).isEqualTo(BookingSeriesStatus.ACTIVE);
    }

    @Test
    @DisplayName("Alternate Room: Resolves conflicting occurrence by booking an alternate room")
    void testResolveOccurrenceWithAlternateRoom() throws Exception {
        LocalDate startDate = LocalDate.now().plusDays(2);
        LocalTime startTime = LocalTime.of(11, 0);
        LocalTime endTime = LocalTime.of(12, 0);

        // Room Alpha has a conflict on day 1
        LocalDate conflictDate = startDate;
        OffsetDateTime startUtc = conflictDate.atTime(startTime).atZone(ZoneId.of("Asia/Kolkata")).toOffsetDateTime();
        OffsetDateTime endUtc = conflictDate.atTime(endTime).atZone(ZoneId.of("Asia/Kolkata")).toOffsetDateTime();
        bookingRepository.save(Booking.builder()
                .room(roomAlpha)
                .user(userBob)
                .startTime(startUtc)
                .endTime(endUtc)
                .reason("Occupied slot")
                .status(BookingStatus.CONFIRMED)
                .build());

        CreateRecurringBookingRequest request = CreateRecurringBookingRequest.builder()
                .roomId(roomAlpha.getId())
                .seriesName("Client Workshop")
                .frequency(RecurrenceFrequency.DAILY)
                .startDate(startDate)
                .occurrenceCount(1)
                .startLocalTime(startTime)
                .endLocalTime(endTime)
                .reason("Workshop")
                .build();

        String respStr = mockMvc.perform(post("/api/bookings/recurring")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long seriesId = objectMapper.readTree(respStr).get("seriesId").asLong();

        // Resolve occurrence 1 with Room Beta
        ResolveAlternateRoomRequest altReq = ResolveAlternateRoomRequest.builder()
                .alternateRoomId(roomBeta.getId())
                .build();

        mockMvc.perform(post("/api/bookings/recurring/" + seriesId + "/occurrences/1/alternate-room")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(altReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.roomId").value(roomBeta.getId()));

        Booking resolved = bookingRepository.findBySeriesIdAndOccurrenceIndex(seriesId, 1).orElseThrow();
        assertThat(resolved.getRoom().getId()).isEqualTo(roomBeta.getId());
        assertThat(resolved.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Contact Admin: Creates admin_requests entry for unresolved occurrence")
    void testContactAdminForOccurrence() throws Exception {
        LocalDate startDate = LocalDate.now().plusDays(2);

        CreateRecurringBookingRequest request = CreateRecurringBookingRequest.builder()
                .roomId(roomAlpha.getId())
                .seriesName("Executive Review")
                .frequency(RecurrenceFrequency.DAILY)
                .startDate(startDate)
                .occurrenceCount(2)
                .startLocalTime(LocalTime.of(17, 0))
                .endLocalTime(LocalTime.of(18, 0))
                .reason("Review")
                .build();

        String respStr = mockMvc.perform(post("/api/bookings/recurring")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long seriesId = objectMapper.readTree(respStr).get("seriesId").asLong();

        ContactAdminOccurrenceRequest contactReq = ContactAdminOccurrenceRequest.builder()
                .message("Need executive room approval")
                .build();

        mockMvc.perform(post("/api/bookings/recurring/" + seriesId + "/occurrences/1/contact-admin")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contactReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestType").value("RECURRING_CONFLICT"));

        List<AdminRequest> adminReqs = adminRequestRepository.findAll();
        assertThat(adminReqs).isNotEmpty();
        assertThat(adminReqs.get(0).getBookingSeries().getId()).isEqualTo(seriesId);
    }

    @Test
    @DisplayName("Cross-Midnight Recurring Booking: Successfully generates and books interval spanning across midnight")
    void testCrossMidnightRecurringBooking() throws Exception {
        LocalDate startDate = LocalDate.now().plusDays(2);

        CreateRecurringBookingRequest request = CreateRecurringBookingRequest.builder()
                .roomId(roomAlpha.getId())
                .seriesName("Night Shift Handover")
                .frequency(RecurrenceFrequency.DAILY)
                .startDate(startDate)
                .occurrenceCount(2)
                .startLocalTime(LocalTime.of(23, 30))
                .endLocalTime(LocalTime.of(0, 30))
                .reason("Cross Midnight Sync")
                .build();

        mockMvc.perform(post("/api/bookings/recurring")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.confirmedCount").value(2))
                .andExpect(jsonPath("$.seriesStatus").value("ACTIVE"));
    }

    @Test
    @DisplayName("Focused Regression: Weekly Monday preview with lazy location proxy returns 200 with 4 available occurrences and no persisted bookings")
    void testWeeklyMondayPreviewReturnsAvailableWithoutPersistingBookings() throws Exception {
        CreateRecurringBookingRequest request = CreateRecurringBookingRequest.builder()
                .roomId(roomAlpha.getId())
                .seriesName("Task 8 Preview Test")
                .frequency(RecurrenceFrequency.WEEKLY)
                .startDate(LocalDate.parse("2026-09-07"))
                .endDate(null)
                .occurrenceCount(4)
                .startLocalTime(LocalTime.parse("10:00:00"))
                .endLocalTime(LocalTime.parse("11:00:00"))
                .daysOfWeek(List.of("MONDAY"))
                .dayOfMonth(null)
                .reason("Task 8 recurring preview verification")
                .build();

        mockMvc.perform(post("/api/bookings/recurring/preview")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seriesName").value("Task 8 Preview Test"))
                .andExpect(jsonPath("$.roomId").value(roomAlpha.getId()))
                .andExpect(jsonPath("$.frequency").value("WEEKLY"))
                .andExpect(jsonPath("$.totalOccurrences").value(4))
                .andExpect(jsonPath("$.availableCount").value(4))
                .andExpect(jsonPath("$.conflictCount").value(0))
                .andExpect(jsonPath("$.occurrences").isArray())
                .andExpect(jsonPath("$.occurrences.length()").value(4))
                .andExpect(jsonPath("$.occurrences[0].date").value("2026-09-07"))
                .andExpect(jsonPath("$.occurrences[0].startTime").value("2026-09-07T04:30:00Z"))
                .andExpect(jsonPath("$.occurrences[0].endTime").value("2026-09-07T05:30:00Z"))
                .andExpect(jsonPath("$.occurrences[0].availability").value("AVAILABLE"))
                .andExpect(jsonPath("$.occurrences[1].date").value("2026-09-14"))
                .andExpect(jsonPath("$.occurrences[2].date").value("2026-09-21"))
                .andExpect(jsonPath("$.occurrences[3].date").value("2026-09-28"));

        // Guarantee preview is strictly read-only and does not persist any bookings or series
        assertThat(bookingRepository.count()).isEqualTo(0);
        assertThat(bookingSeriesRepository.count()).isEqualTo(0);
    }
}
