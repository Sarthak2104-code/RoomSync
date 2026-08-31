package com.roomsync.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomsync.booking.dto.CreateBookingRequest;
import com.roomsync.booking.dto.CreateRecurringBookingRequest;
import com.roomsync.booking.dto.RescheduleBookingRequest;
import com.roomsync.booking.entity.Booking;
import com.roomsync.booking.entity.BookingStatus;
import com.roomsync.booking.entity.RecurrenceFrequency;
import com.roomsync.booking.repository.BookingRepository;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

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
class BookingAccessRestrictionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Location location;
    private Room room1;
    private Room room2;
    private User testUser;
    private String userToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE notification_outbox, audit_logs, agent_actions, idempotency_records, agent_operations, bookings, booking_series, rooms, users, locations RESTART IDENTITY CASCADE");

        location = locationRepository.save(Location.builder()
                .name("Mumbai HQ")
                .code("MUM-HQ")
                .timezone("Asia/Kolkata")
                .active(true)
                .build());

        Role userRole = roleRepository.findByName("USER").orElseGet(() ->
                roleRepository.save(Role.builder().name("USER").build()));

        testUser = userRepository.save(User.builder()
                .wissenId("WT5128")
                .name("Alice Developer")
                .email("alice@wissen.com")
                .password("hashedPassword")
                .role(userRole)
                .location(location)
                .active(true)
                .bookingEnabled(false) // User is blocked from booking
                .build());

        userToken = jwtTokenProvider.generateAccessToken(testUser.getId(), testUser.getWissenId(), testUser.getEmail(), "USER", location.getId());

        room1 = roomRepository.save(Room.builder()
                .name("Conference Alpha")
                .capacity(12)
                .location(location)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build());

        room2 = roomRepository.save(Room.builder()
                .name("Conference Beta")
                .capacity(16)
                .location(location)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build());
    }

    @Test
    @DisplayName("Blocked user cannot create single booking -> 403 Forbidden")
    void testBlockedUserCannotCreateBooking() throws Exception {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .roomId(room1.getId())
                .startTime(OffsetDateTime.parse("2026-09-15T09:00:00Z"))
                .endTime(OffsetDateTime.parse("2026-09-15T10:00:00Z"))
                .reason("Sprint Planning")
                .build();

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Your account is currently restricted from creating room bookings. Please contact an administrator."));

        assertThat(bookingRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("Blocked user cannot reschedule existing booking -> 403 Forbidden")
    void testBlockedUserCannotRescheduleBooking() throws Exception {
        // Create an existing booking manually in DB
        Booking existingBooking = bookingRepository.save(Booking.builder()
                .room(room1)
                .user(testUser)
                .startTime(OffsetDateTime.parse("2026-09-15T09:00:00Z"))
                .endTime(OffsetDateTime.parse("2026-09-15T10:00:00Z"))
                .reason("Existing Sprint Planning")
                .status(BookingStatus.CONFIRMED)
                .build());

        RescheduleBookingRequest request = RescheduleBookingRequest.builder()
                .startTime(OffsetDateTime.parse("2026-09-15T11:00:00Z"))
                .endTime(OffsetDateTime.parse("2026-09-15T12:00:00Z"))
                .roomId(room2.getId())
                .build();

        mockMvc.perform(put("/api/bookings/" + existingBooking.getId() + "/reschedule")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Blocked user cannot create or preview recurring booking series -> 403 Forbidden")
    void testBlockedUserCannotCreateRecurringBooking() throws Exception {
        CreateRecurringBookingRequest request = CreateRecurringBookingRequest.builder()
                .seriesName("Weekly Architecture Sync")
                .reason("Weekly Architecture Sync")
                .roomId(room1.getId())
                .frequency(RecurrenceFrequency.WEEKLY)
                .daysOfWeek(List.of("TUESDAY"))
                .startDate(LocalDate.parse("2026-09-15"))
                .endDate(LocalDate.parse("2026-10-15"))
                .startLocalTime(LocalTime.of(10, 0))
                .endLocalTime(LocalTime.of(11, 0))
                .build();

        // Preview should be blocked
        mockMvc.perform(post("/api/bookings/recurring/preview")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        // Create should be blocked
        mockMvc.perform(post("/api/bookings/recurring")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Blocked user CAN cancel their existing booking -> 204 No Content")
    void testBlockedUserCanCancelExistingBooking() throws Exception {
        Booking existingBooking = bookingRepository.save(Booking.builder()
                .room(room1)
                .user(testUser)
                .startTime(OffsetDateTime.parse("2026-09-15T09:00:00Z"))
                .endTime(OffsetDateTime.parse("2026-09-15T10:00:00Z"))
                .reason("Existing Sprint Planning")
                .status(BookingStatus.CONFIRMED)
                .build());

        mockMvc.perform(delete("/api/bookings/" + existingBooking.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());

        Booking cancelled = bookingRepository.findById(existingBooking.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("Blocked user CAN view their existing bookings and profile")
    void testBlockedUserCanViewBookingsAndProfile() throws Exception {
        Booking existingBooking = bookingRepository.save(Booking.builder()
                .room(room1)
                .user(testUser)
                .startTime(OffsetDateTime.parse("2026-09-15T09:00:00Z"))
                .endTime(OffsetDateTime.parse("2026-09-15T10:00:00Z"))
                .reason("Existing Sprint Planning")
                .status(BookingStatus.CONFIRMED)
                .build());

        // Can view single booking
        mockMvc.perform(get("/api/bookings/" + existingBooking.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existingBooking.getId()));

        // Can view booking list
        mockMvc.perform(get("/api/bookings/my")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));

        // Can view profile with bookingEnabled: false
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingEnabled").value(false));
    }

    @Test
    @DisplayName("Unblocking user immediately restores ability to create bookings")
    void testUnblockingUserRestoresBooking() throws Exception {
        // Unblock user
        testUser.setBookingEnabled(true);
        userRepository.save(testUser);

        CreateBookingRequest request = CreateBookingRequest.builder()
                .roomId(room1.getId())
                .startTime(OffsetDateTime.parse("2026-09-15T09:00:00Z"))
                .endTime(OffsetDateTime.parse("2026-09-15T10:00:00Z"))
                .reason("Restored Sprint Planning")
                .build();

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(bookingRepository.count()).isEqualTo(1);
    }
}
