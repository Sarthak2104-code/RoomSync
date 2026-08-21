package com.roomsync.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomsync.booking.dto.BookingResponse;
import com.roomsync.booking.dto.CreateBookingRequest;
import com.roomsync.booking.dto.RescheduleBookingRequest;
import com.roomsync.booking.entity.Booking;
import com.roomsync.booking.entity.BookingStatus;
import com.roomsync.booking.repository.BookingRepository;
import com.roomsync.location.entity.Location;
import com.roomsync.location.repository.LocationRepository;
import com.roomsync.room.entity.Room;
import com.roomsync.room.entity.RoomStatus;
import com.roomsync.room.repository.RoomRepository;
import com.roomsync.user.entity.User;
import com.roomsync.user.entity.UserRole;
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
class BookingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Location location;
    private User user1;
    private User user2;
    private Room room1;
    private Room lockedRoom;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE bookings, rooms, users, locations RESTART IDENTITY CASCADE");

        location = locationRepository.save(Location.builder()
                .name("Mumbai")
                .code("MUM")
                .active(true)
                .build());

        user1 = userRepository.save(User.builder()
                .name("Alice")
                .email("alice@roomsync.com")
                .password("hash1")
                .role(UserRole.USER)
                .location(location)
                .build());

        user2 = userRepository.save(User.builder()
                .name("Bob")
                .email("bob@roomsync.com")
                .password("hash2")
                .role(UserRole.USER)
                .location(location)
                .build());

        room1 = roomRepository.save(Room.builder()
                .location(location)
                .name("Taj Mahal")
                .capacity(10)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build());

        lockedRoom = roomRepository.save(Room.builder()
                .location(location)
                .name("Qutub Minar")
                .capacity(8)
                .status(RoomStatus.LOCKED)
                .active(true)
                .build());
    }

    @Test
    @DisplayName("End-to-End: Create, Overlap Rejection, Adjacent Allowed, Ownership, Reschedule, Soft Cancel, Rebook")
    void testFullBookingLifecycle() throws Exception {
        OffsetDateTime slot1Start = OffsetDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime slot1End = slot1Start.plusHours(1);

        // 1. Create Booking
        CreateBookingRequest createRequest = CreateBookingRequest.builder()
                .roomId(room1.getId())
                .startTime(slot1Start)
                .endTime(slot1End)
                .build();

        String responseStr = mockMvc.perform(post("/api/bookings")
                        .header("X-User-Id", user1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.roomId").value(room1.getId()))
                .andExpect(jsonPath("$.userId").value(user1.getId()))
                .andReturn().getResponse().getContentAsString();

        BookingResponse createdBooking = objectMapper.readValue(responseStr, BookingResponse.class);
        Long bookingId = createdBooking.getId();

        // 2. Reject Overlapping Booking (10:30 - 11:30)
        CreateBookingRequest overlapRequest = CreateBookingRequest.builder()
                .roomId(room1.getId())
                .startTime(slot1Start.plusMinutes(30))
                .endTime(slot1End.plusMinutes(30))
                .build();

        mockMvc.perform(post("/api/bookings")
                        .header("X-User-Id", user2.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overlapRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));

        // 3. Allow Adjacent Booking (11:00 - 12:00)
        CreateBookingRequest adjacentRequest = CreateBookingRequest.builder()
                .roomId(room1.getId())
                .startTime(slot1End)
                .endTime(slot1End.plusHours(1))
                .build();

        mockMvc.perform(post("/api/bookings")
                        .header("X-User-Id", user2.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adjacentRequest)))
                .andExpect(status().isCreated());

        // 4. Get Booking by Owner -> 200 OK
        mockMvc.perform(get("/api/bookings/" + bookingId)
                        .header("X-User-Id", user1.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId));

        // 5. Get Booking by Non-Owner -> 403 FORBIDDEN
        mockMvc.perform(get("/api/bookings/" + bookingId)
                        .header("X-User-Id", user2.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        // 6. Get My Bookings -> 200 OK
        mockMvc.perform(get("/api/bookings/my")
                        .header("X-User-Id", user1.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(bookingId));

        // 7. Reschedule Booking (Move to 14:00 - 15:00)
        OffsetDateTime rescheduleStart = slot1Start.withHour(14);
        OffsetDateTime rescheduleEnd = slot1Start.withHour(15);
        RescheduleBookingRequest rescheduleRequest = RescheduleBookingRequest.builder()
                .startTime(rescheduleStart)
                .endTime(rescheduleEnd)
                .build();

        mockMvc.perform(put("/api/bookings/" + bookingId)
                        .header("X-User-Id", user1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rescheduleRequest)))
                .andExpect(status().isOk());

        // 8. Soft Cancel Booking -> 204 NO CONTENT
        mockMvc.perform(delete("/api/bookings/" + bookingId)
                        .header("X-User-Id", user1.getId()))
                .andExpect(status().isNoContent());

        // Verify DB row remains with status = CANCELLED
        Booking cancelledDbBooking = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(cancelledDbBooking.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        // 9. Re-booking the old slot is now allowed because previous booking is CANCELLED
        CreateBookingRequest rebookRequest = CreateBookingRequest.builder()
                .roomId(room1.getId())
                .startTime(rescheduleStart)
                .endTime(rescheduleEnd)
                .build();

        mockMvc.perform(post("/api/bookings")
                        .header("X-User-Id", user2.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rebookRequest)))
                .andExpect(status().isCreated());

        // 10. Booking locked room rejected -> 409 CONFLICT
        CreateBookingRequest lockedRoomRequest = CreateBookingRequest.builder()
                .roomId(lockedRoom.getId())
                .startTime(slot1Start.plusDays(1))
                .endTime(slot1End.plusDays(1))
                .build();

        mockMvc.perform(post("/api/bookings")
                        .header("X-User-Id", user1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lockedRoomRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }
}
