package com.roomsync.room.service;

import com.roomsync.common.response.PageResponse;
import com.roomsync.location.entity.Location;
import com.roomsync.location.exception.LocationNotActiveException;
import com.roomsync.location.exception.LocationNotFoundException;
import com.roomsync.location.exception.UnauthorizedLocationAccessException;
import com.roomsync.location.repository.LocationRepository;
import com.roomsync.room.dto.CreateRoomRequest;
import com.roomsync.room.dto.RoomResponse;
import com.roomsync.room.dto.UpdateRoomRequest;
import com.roomsync.room.entity.Room;
import com.roomsync.room.entity.RoomStatus;
import com.roomsync.room.exception.DuplicateRoomNameException;
import com.roomsync.room.exception.RoomAlreadyInactiveException;
import com.roomsync.room.exception.RoomAlreadyLockedException;
import com.roomsync.room.exception.RoomAlreadyUnlockedException;
import com.roomsync.room.exception.RoomNotFoundException;
import com.roomsync.room.repository.RoomRepository;
import com.roomsync.user.entity.User;
import com.roomsync.user.entity.UserRole;
import com.roomsync.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RoomService roomService;

    private Location locationMumbai;
    private Location locationPune;
    private Location inactiveLocation;
    private User mumbaiUser;
    private User puneUser;
    private User adminUser;
    private Room availableRoom;
    private Room lockedRoom;
    private Room inactiveRoom;

    @BeforeEach
    void setUp() {
        locationMumbai = Location.builder().id(1L).name("Mumbai").code("MUM").active(true).build();
        locationPune = Location.builder().id(2L).name("Pune").code("PUN").active(true).build();
        inactiveLocation = Location.builder().id(3L).name("Delhi").code("DEL").active(false).build();

        mumbaiUser = User.builder().id(10L).name("Alice").email("alice@mumbai.com").role(UserRole.USER).location(locationMumbai).build();
        puneUser = User.builder().id(20L).name("Bob").email("bob@pune.com").role(UserRole.USER).location(locationPune).build();
        adminUser = User.builder().id(30L).name("Admin").email("admin@roomsync.com").role(UserRole.ADMIN).location(locationMumbai).build();

        availableRoom = Room.builder()
                .id(1L)
                .location(locationMumbai)
                .name("Taj Mahal")
                .capacity(10)
                .description("Large conference room")
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        lockedRoom = Room.builder()
                .id(2L)
                .location(locationMumbai)
                .name("Qutub Minar")
                .capacity(6)
                .description("Small meeting room")
                .status(RoomStatus.LOCKED)
                .active(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        inactiveRoom = Room.builder()
                .id(3L)
                .location(locationMumbai)
                .name("Red Fort")
                .capacity(15)
                .status(RoomStatus.AVAILABLE)
                .active(false)
                .build();
    }

    @Nested
    @DisplayName("createRoom tests")
    class CreateRoomTests {

        @Test
        @DisplayName("Should create room successfully with location, AVAILABLE status and active=true")
        void shouldCreateRoomSuccessfully() {
            CreateRoomRequest request = CreateRoomRequest.builder()
                    .locationId(1L)
                    .name("Taj Mahal")
                    .capacity(10)
                    .description("Large conference room")
                    .build();

            when(userRepository.findById(10L)).thenReturn(Optional.of(mumbaiUser));
            when(locationRepository.findById(1L)).thenReturn(Optional.of(locationMumbai));
            when(roomRepository.existsByLocationIdAndNameIgnoreCase(1L, "Taj Mahal")).thenReturn(false);
            when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> {
                Room toSave = invocation.getArgument(0);
                toSave.setId(1L);
                return toSave;
            });

            RoomResponse response = roomService.createRoom(10L, request);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getName()).isEqualTo("Taj Mahal");
            assertThat(response.getCapacity()).isEqualTo(10);
            assertThat(response.getLocation().getCode()).isEqualTo("MUM");
            assertThat(response.getStatus()).isEqualTo(RoomStatus.AVAILABLE);
            assertThat(response.isActive()).isTrue();
            verify(roomRepository).save(any(Room.class));
        }

        @Test
        @DisplayName("Should throw UnauthorizedLocationAccessException when USER creates room in another location")
        void shouldThrowWhenUserCreatesRoomInOtherLocation() {
            CreateRoomRequest request = CreateRoomRequest.builder()
                    .locationId(2L)
                    .name("Shivaji Hall")
                    .capacity(10)
                    .build();

            when(userRepository.findById(10L)).thenReturn(Optional.of(mumbaiUser));
            when(locationRepository.findById(2L)).thenReturn(Optional.of(locationPune));

            assertThatThrownBy(() -> roomService.createRoom(10L, request))
                    .isInstanceOf(UnauthorizedLocationAccessException.class);
        }

        @Test
        @DisplayName("Should allow ADMIN to create room in any active location")
        void shouldAllowAdminToCreateRoomInAnyLocation() {
            CreateRoomRequest request = CreateRoomRequest.builder()
                    .locationId(2L)
                    .name("Shivaji Hall")
                    .capacity(10)
                    .build();

            when(userRepository.findById(30L)).thenReturn(Optional.of(adminUser));
            when(locationRepository.findById(2L)).thenReturn(Optional.of(locationPune));
            when(roomRepository.existsByLocationIdAndNameIgnoreCase(2L, "Shivaji Hall")).thenReturn(false);
            when(roomRepository.save(any(Room.class))).thenAnswer(i -> {
                Room r = i.getArgument(0);
                r.setId(100L);
                return r;
            });

            RoomResponse response = roomService.createRoom(30L, request);
            assertThat(response.getLocation().getCode()).isEqualTo("PUN");
        }

        @Test
        @DisplayName("Should throw LocationNotActiveException when creating room in inactive location")
        void shouldThrowWhenLocationInactive() {
            CreateRoomRequest request = CreateRoomRequest.builder()
                    .locationId(3L)
                    .name("India Gate")
                    .capacity(10)
                    .build();

            when(userRepository.findById(30L)).thenReturn(Optional.of(adminUser));
            when(locationRepository.findById(3L)).thenReturn(Optional.of(inactiveLocation));

            assertThatThrownBy(() -> roomService.createRoom(30L, request))
                    .isInstanceOf(LocationNotActiveException.class);
        }

        @Test
        @DisplayName("Should throw DuplicateRoomNameException when room name already exists in same location")
        void shouldThrowDuplicateWhenNameExistsInLocation() {
            CreateRoomRequest request = CreateRoomRequest.builder()
                    .locationId(1L)
                    .name("Taj Mahal")
                    .capacity(10)
                    .build();

            when(userRepository.findById(10L)).thenReturn(Optional.of(mumbaiUser));
            when(locationRepository.findById(1L)).thenReturn(Optional.of(locationMumbai));
            when(roomRepository.existsByLocationIdAndNameIgnoreCase(1L, "Taj Mahal")).thenReturn(true);

            assertThatThrownBy(() -> roomService.createRoom(10L, request))
                    .isInstanceOf(DuplicateRoomNameException.class);
        }
    }

    @Nested
    @DisplayName("getRooms tests")
    class GetRoomsTests {

        @Test
        @DisplayName("Should return paginated active rooms for user's location")
        void shouldReturnPaginatedRoomsForUser() {
            Pageable pageable = PageRequest.of(0, 10, Sort.by("name").ascending());
            Page<Room> page = new PageImpl<>(List.of(availableRoom, lockedRoom), pageable, 2);

            when(userRepository.findById(10L)).thenReturn(Optional.of(mumbaiUser));
            when(roomRepository.findAllByLocationIdAndActiveTrue(eq(1L), any(Pageable.class))).thenReturn(page);

            PageResponse<RoomResponse> result = roomService.getRooms(10L, null, pageable);

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should throw UnauthorizedLocationAccessException when user queries another location")
        void shouldThrowWhenUserQueriesOtherLocation() {
            when(userRepository.findById(10L)).thenReturn(Optional.of(mumbaiUser));

            assertThatThrownBy(() -> roomService.getRooms(10L, 2L, PageRequest.of(0, 10)))
                    .isInstanceOf(UnauthorizedLocationAccessException.class);
        }
    }

    @Nested
    @DisplayName("getRoomById tests")
    class GetRoomByIdTests {

        @Test
        @DisplayName("Should return active room by ID when user has location access")
        void shouldReturnRoomById() {
            when(userRepository.findById(10L)).thenReturn(Optional.of(mumbaiUser));
            when(roomRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(availableRoom));

            RoomResponse response = roomService.getRoomById(10L, 1L);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getName()).isEqualTo("Taj Mahal");
        }

        @Test
        @DisplayName("Should throw UnauthorizedLocationAccessException when user tries to get room in other location")
        void shouldThrowWhenUserGetsRoomInOtherLocation() {
            when(userRepository.findById(20L)).thenReturn(Optional.of(puneUser));
            when(roomRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(availableRoom));

            assertThatThrownBy(() -> roomService.getRoomById(20L, 1L))
                    .isInstanceOf(UnauthorizedLocationAccessException.class);
        }
    }

    @Nested
    @DisplayName("lock / unlock tests")
    class LockUnlockTests {

        @Test
        @DisplayName("Should lock AVAILABLE room successfully")
        void shouldLockAvailableRoom() {
            when(userRepository.findById(10L)).thenReturn(Optional.of(mumbaiUser));
            when(roomRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(availableRoom));
            when(roomRepository.save(any(Room.class))).thenAnswer(i -> i.getArgument(0));

            RoomResponse response = roomService.lockRoom(10L, 1L);

            assertThat(response.getStatus()).isEqualTo(RoomStatus.LOCKED);
        }

        @Test
        @DisplayName("Should unlock LOCKED room successfully")
        void shouldUnlockLockedRoom() {
            when(userRepository.findById(10L)).thenReturn(Optional.of(mumbaiUser));
            when(roomRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(lockedRoom));
            when(roomRepository.save(any(Room.class))).thenAnswer(i -> i.getArgument(0));

            RoomResponse response = roomService.unlockRoom(10L, 2L);

            assertThat(response.getStatus()).isEqualTo(RoomStatus.AVAILABLE);
        }
    }

    @Nested
    @DisplayName("deactivateRoom tests")
    class DeactivateRoomTests {

        @Test
        @DisplayName("Should soft-deactivate active room")
        void shouldSoftDeactivateActiveRoom() {
            when(userRepository.findById(10L)).thenReturn(Optional.of(mumbaiUser));
            when(roomRepository.findById(1L)).thenReturn(Optional.of(availableRoom));

            roomService.deactivateRoom(10L, 1L);

            assertThat(availableRoom.isActive()).isFalse();
            verify(roomRepository).save(availableRoom);
        }
    }
}
