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
import com.roomsync.room.exception.RoomAlreadyActiveException;
import com.roomsync.room.exception.RoomAlreadyInactiveException;
import com.roomsync.room.exception.RoomAlreadyLockedException;
import com.roomsync.room.exception.RoomAlreadyUnlockedException;
import com.roomsync.room.exception.RoomNotFoundException;
import com.roomsync.room.repository.RoomRepository;
import com.roomsync.user.entity.User;
import com.roomsync.user.entity.UserRole;
import com.roomsync.user.exception.UserNotFoundException;
import com.roomsync.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Service managing Room lifecycle, location scoping, and access control.
 * Room names are unique within their associated Location.
 * Normal users can only access and manage rooms within their assigned Location.
 * Administrators can access and manage rooms across all Locations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoomService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "name", "capacity", "status", "createdAt", "updatedAt"
    );

    private final RoomRepository roomRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;

    @Transactional
    public RoomResponse createRoom(Long userId, CreateRoomRequest request) {
        User caller = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new LocationNotFoundException(request.getLocationId()));

        if (!location.isActive()) {
            throw new LocationNotActiveException(
                    String.format("Location '%s' is inactive and cannot receive new rooms", location.getName())
            );
        }

        if (caller.getRoleEnum() == UserRole.USER && !caller.getLocation().getId().equals(request.getLocationId())) {
            throw new UnauthorizedLocationAccessException("User is not authorized to create rooms in another location");
        }

        String normalizedName = request.getName().trim();
        if (roomRepository.existsByLocationIdAndNameIgnoreCase(location.getId(), normalizedName)) {
            throw new DuplicateRoomNameException(normalizedName, location.getName());
        }

        Room room = Room.builder()
                .location(location)
                .name(normalizedName)
                .capacity(request.getCapacity())
                .description(request.getDescription())
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build();

        Room savedRoom = roomRepository.save(room);
        log.info("Created new room with id: {} and name: '{}' in location: '{}'", savedRoom.getId(), savedRoom.getName(), location.getCode());
        return RoomResponse.fromEntity(savedRoom);
    }

    @Transactional(readOnly = true)
    public PageResponse<RoomResponse> getRooms(Long userId, Long locationId, Pageable pageable) {
        User caller = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Pageable validatedPageable = sanitizePageable(pageable);
        Page<Room> roomPage;

        if (caller.getRoleEnum() == UserRole.USER) {
            if (locationId != null && !locationId.equals(caller.getLocation().getId())) {
                throw new UnauthorizedLocationAccessException("User cannot view rooms in another location");
            }
            roomPage = roomRepository.findAllByLocationIdAndActiveTrue(caller.getLocation().getId(), validatedPageable);
        } else {
            if (locationId != null) {
                roomPage = roomRepository.findAllByLocationIdAndActiveTrue(locationId, validatedPageable);
            } else {
                roomPage = roomRepository.findAllByActiveTrue(validatedPageable);
            }
        }

        return PageResponse.fromPage(roomPage, RoomResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public RoomResponse getRoomById(Long userId, Long id) {
        User caller = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Room room = roomRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new RoomNotFoundException(id));

        if (caller.getRoleEnum() == UserRole.USER && !room.getLocation().getId().equals(caller.getLocation().getId())) {
            throw new UnauthorizedLocationAccessException("User is not authorized to access rooms in another location");
        }

        return RoomResponse.fromEntity(room);
    }

    @Transactional
    public RoomResponse updateRoom(Long userId, Long id, UpdateRoomRequest request) {
        User caller = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Room room = roomRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new RoomNotFoundException(id));

        if (caller.getRoleEnum() == UserRole.USER && !room.getLocation().getId().equals(caller.getLocation().getId())) {
            throw new UnauthorizedLocationAccessException("User is not authorized to modify rooms in another location");
        }

        String normalizedName = request.getName().trim();
        if (roomRepository.existsByLocationIdAndNameIgnoreCaseAndIdNot(room.getLocation().getId(), normalizedName, id)) {
            throw new DuplicateRoomNameException(normalizedName, room.getLocation().getName());
        }

        room.setName(normalizedName);
        room.setCapacity(request.getCapacity());
        room.setDescription(request.getDescription());

        Room updatedRoom = roomRepository.save(room);
        log.info("Updated room with id: {}", updatedRoom.getId());
        return RoomResponse.fromEntity(updatedRoom);
    }

    @Transactional
    public RoomResponse lockRoom(Long userId, Long id) {
        User caller = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Room room = roomRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new RoomNotFoundException(id));

        if (caller.getRoleEnum() == UserRole.USER && !room.getLocation().getId().equals(caller.getLocation().getId())) {
            throw new UnauthorizedLocationAccessException("User is not authorized to lock rooms in another location");
        }

        if (room.getStatus() == RoomStatus.LOCKED) {
            throw new RoomAlreadyLockedException(id);
        }

        room.setStatus(RoomStatus.LOCKED);
        Room updatedRoom = roomRepository.save(room);
        log.info("Locked room with id: {}", updatedRoom.getId());
        return RoomResponse.fromEntity(updatedRoom);
    }

    @Transactional
    public RoomResponse unlockRoom(Long userId, Long id) {
        User caller = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Room room = roomRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new RoomNotFoundException(id));

        if (caller.getRoleEnum() == UserRole.USER && !room.getLocation().getId().equals(caller.getLocation().getId())) {
            throw new UnauthorizedLocationAccessException("User is not authorized to unlock rooms in another location");
        }

        if (room.getStatus() == RoomStatus.AVAILABLE) {
            throw new RoomAlreadyUnlockedException(id);
        }

        room.setStatus(RoomStatus.AVAILABLE);
        Room updatedRoom = roomRepository.save(room);
        log.info("Unlocked room with id: {}", updatedRoom.getId());
        return RoomResponse.fromEntity(updatedRoom);
    }

    @Transactional
    public RoomResponse activateRoom(Long userId, Long id) {
        User caller = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new RoomNotFoundException(id));

        if (caller.getRoleEnum() == UserRole.USER && !room.getLocation().getId().equals(caller.getLocation().getId())) {
            throw new UnauthorizedLocationAccessException("User is not authorized to activate rooms in another location");
        }

        if (room.isActive()) {
            throw new RoomAlreadyActiveException(id);
        }

        room.setActive(true);
        Room updatedRoom = roomRepository.save(room);
        log.info("Activated room with id: {}", id);
        return RoomResponse.fromEntity(updatedRoom);
    }

    @Transactional
    public void deactivateRoom(Long userId, Long id) {
        User caller = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new RoomNotFoundException(id));

        if (caller.getRoleEnum() == UserRole.USER && !room.getLocation().getId().equals(caller.getLocation().getId())) {
            throw new UnauthorizedLocationAccessException("User is not authorized to deactivate rooms in another location");
        }

        if (!room.isActive()) {
            throw new RoomAlreadyInactiveException(id);
        }

        room.setActive(false);
        roomRepository.save(room);
        log.info("Soft-deactivated room with id: {}", id);
    }

    private Pageable sanitizePageable(Pageable pageable) {
        int page = Math.max(pageable.getPageNumber(), 0);
        int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE);

        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Direction.ASC, "name");
        } else {
            for (Sort.Order order : sort) {
                if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                    throw new IllegalArgumentException(
                            String.format("Invalid sort property '%s'. Allowed properties: %s",
                                    order.getProperty(), ALLOWED_SORT_FIELDS)
                    );
                }
            }
        }

        return PageRequest.of(page, size, sort);
    }
}
