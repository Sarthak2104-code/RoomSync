package com.roomsync.booking;

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
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.springframework.test.context.ActiveProfiles;

/**
 * Category C: Direct Database Constraint Tests
 * Verifies that PostgreSQL's 'no_overlapping_bookings' exclusion constraint
 * independently rejects overlapping CONFIRMED bookings even when bypassing application logic.
 */
@SpringBootTest
@ActiveProfiles("test")
class BookingExclusionConstraintTest {

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

    private Long userId;
    private Long room1Id;
    private Long room2Id;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE bookings, rooms, users, locations RESTART IDENTITY CASCADE");

        Location location = locationRepository.save(Location.builder()
                .name("Mumbai")
                .code("MUM")
                .active(true)
                .timezone("Asia/Kolkata")
                .build());

        Role userRole = roleRepository.findByName("USER").orElseGet(() ->
                roleRepository.save(Role.builder().name("USER").build()));

        User user = userRepository.save(User.builder()
                .name("Alice")
                .email("alice@roomsync.com")
                .password("hash")
                .role(userRole)
                .location(location)
                .build());
        userId = user.getId();

        Room room1 = roomRepository.save(Room.builder()
                .location(location)
                .name("Room Alpha")
                .capacity(10)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build());
        room1Id = room1.getId();

        Room room2 = roomRepository.save(Room.builder()
                .location(location)
                .name("Room Beta")
                .capacity(15)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build());
        room2Id = room2.getId();
    }

    @Test
    @DisplayName("Constraint Test 1: Same room + overlapping + CONFIRMED must be rejected by no_overlapping_bookings")
    void shouldRejectOverlappingConfirmedBookings() {
        OffsetDateTime start1 = OffsetDateTime.parse("2026-08-25T10:00:00Z");
        OffsetDateTime end1 = OffsetDateTime.parse("2026-08-25T11:00:00Z");

        OffsetDateTime start2 = OffsetDateTime.parse("2026-08-25T10:30:00Z");
        OffsetDateTime end2 = OffsetDateTime.parse("2026-08-25T11:30:00Z");

        // Insert first booking
        jdbcTemplate.update(
                "INSERT INTO bookings (room_id, user_id, start_time, end_time, status, reason) VALUES (?, ?, ?, ?, ?, ?)",
                room1Id, userId, start1, end1, "CONFIRMED", "Direct SQL Slot 1"
        );

        // Attempt overlapping insert directly via raw SQL
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO bookings (room_id, user_id, start_time, end_time, status, reason) VALUES (?, ?, ?, ?, ?, ?)",
                room1Id, userId, start2, end2, "CONFIRMED", "Direct SQL Slot 2"
        ))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(thrown -> {
                    DataIntegrityViolationException ex = (DataIntegrityViolationException) thrown;
                    Throwable root = ex.getMostSpecificCause();
                    assertThat(root).isInstanceOf(PSQLException.class);
                    PSQLException psql = (PSQLException) root;
                    assertThat(psql.getServerErrorMessage()).isNotNull();
                    assertThat(psql.getServerErrorMessage().getConstraint()).isEqualTo("no_overlapping_bookings");
                });
    }

    @Test
    @DisplayName("Constraint Test 2: Same room + adjacent + CONFIRMED must be allowed ([) range semantics)")
    void shouldAllowAdjacentConfirmedBookings() {
        OffsetDateTime start1 = OffsetDateTime.parse("2026-08-25T10:00:00Z");
        OffsetDateTime end1 = OffsetDateTime.parse("2026-08-25T11:00:00Z");

        OffsetDateTime start2 = OffsetDateTime.parse("2026-08-25T11:00:00Z");
        OffsetDateTime end2 = OffsetDateTime.parse("2026-08-25T12:00:00Z");

        jdbcTemplate.update(
                "INSERT INTO bookings (room_id, user_id, start_time, end_time, status, reason) VALUES (?, ?, ?, ?, ?, ?)",
                room1Id, userId, start1, end1, "CONFIRMED", "Adjacent Slot 1"
        );

        int rowsInserted = jdbcTemplate.update(
                "INSERT INTO bookings (room_id, user_id, start_time, end_time, status, reason) VALUES (?, ?, ?, ?, ?, ?)",
                room1Id, userId, start2, end2, "CONFIRMED", "Adjacent Slot 2"
        );

        assertThat(rowsInserted).isEqualTo(1);
    }

    @Test
    @DisplayName("Constraint Test 3: Same room + overlapping + CANCELLED must be allowed")
    void shouldAllowOverlappingWhenCancelled() {
        OffsetDateTime start1 = OffsetDateTime.parse("2026-08-25T10:00:00Z");
        OffsetDateTime end1 = OffsetDateTime.parse("2026-08-25T11:00:00Z");

        // Cancelled booking
        jdbcTemplate.update(
                "INSERT INTO bookings (room_id, user_id, start_time, end_time, status, reason) VALUES (?, ?, ?, ?, ?, ?)",
                room1Id, userId, start1, end1, "CANCELLED", "Cancelled Slot"
        );

        // Confirmed booking for the same time slot
        int rowsInserted = jdbcTemplate.update(
                "INSERT INTO bookings (room_id, user_id, start_time, end_time, status, reason) VALUES (?, ?, ?, ?, ?, ?)",
                room1Id, userId, start1, end1, "CONFIRMED", "Active Replacement Slot"
        );

        assertThat(rowsInserted).isEqualTo(1);
    }

    @Test
    @DisplayName("Constraint Test 4: Different rooms + same time + CONFIRMED must be allowed")
    void shouldAllowSameTimeForDifferentRooms() {
        OffsetDateTime start = OffsetDateTime.parse("2026-08-25T10:00:00Z");
        OffsetDateTime end = OffsetDateTime.parse("2026-08-25T11:00:00Z");

        int rows1 = jdbcTemplate.update(
                "INSERT INTO bookings (room_id, user_id, start_time, end_time, status, reason) VALUES (?, ?, ?, ?, ?, ?)",
                room1Id, userId, start, end, "CONFIRMED", "Room 1 Slot"
        );

        int rows2 = jdbcTemplate.update(
                "INSERT INTO bookings (room_id, user_id, start_time, end_time, status, reason) VALUES (?, ?, ?, ?, ?, ?)",
                room2Id, userId, start, end, "CONFIRMED", "Room 2 Slot"
        );

        assertThat(rows1).isEqualTo(1);
        assertThat(rows2).isEqualTo(1);
    }
}
