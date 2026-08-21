package com.roomsync.room.repository;

import com.roomsync.room.entity.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for Room entities.
 * Note: Room names are unique per Location (location_id, LOWER(name)),
 * enforced case-insensitively via the 'ux_rooms_location_name_lower' unique index.
 */
@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    @Query("SELECT COUNT(r) > 0 FROM Room r WHERE r.location.id = :locationId AND LOWER(r.name) = LOWER(:name)")
    boolean existsByLocationIdAndNameIgnoreCase(@Param("locationId") Long locationId, @Param("name") String name);

    @Query("SELECT COUNT(r) > 0 FROM Room r WHERE r.location.id = :locationId AND LOWER(r.name) = LOWER(:name) AND r.id <> :id")
    boolean existsByLocationIdAndNameIgnoreCaseAndIdNot(
            @Param("locationId") Long locationId,
            @Param("name") String name,
            @Param("id") Long id
    );

    Page<Room> findAllByLocationIdAndActiveTrue(Long locationId, Pageable pageable);

    Page<Room> findAllByActiveTrue(Pageable pageable);

    Optional<Room> findByIdAndActiveTrue(Long id);

    /**
     * Acquires a pessimistic write lock (SELECT ... FOR UPDATE) on the specified Room.
     * Serves as the synchronization point for concurrent booking creations, reschedulings, and cancellations.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.id = :roomId")
    Optional<Room> findByIdForUpdate(@Param("roomId") Long roomId);
}
