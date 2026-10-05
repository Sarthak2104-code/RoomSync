package com.roomsync.user.repository;

import com.roomsync.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByWissenIdIgnoreCase(String wissenId);
    boolean existsByWissenIdIgnoreCase(String wissenId);

    @Query("""
        SELECT u FROM User u
        WHERE (:searchPattern IS NULL OR
               LOWER(u.name) LIKE :searchPattern OR
               LOWER(u.email) LIKE :searchPattern OR
               LOWER(u.wissenId) LIKE :searchPattern)
          AND (:locationId IS NULL OR u.location.id = :locationId)
          AND (:bookingEnabled IS NULL OR u.bookingEnabled = :bookingEnabled)
          AND (:active IS NULL OR u.active = :active)
          AND (:role IS NULL OR u.role.name = :role)
    """)
    Page<User> findAllByAdminFilters(
            @Param("searchPattern") String searchPattern,
            @Param("locationId") Long locationId,
            @Param("bookingEnabled") Boolean bookingEnabled,
            @Param("active") Boolean active,
            @Param("role") String role,
            Pageable pageable
    );
}
