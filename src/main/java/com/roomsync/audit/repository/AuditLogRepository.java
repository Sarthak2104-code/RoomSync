package com.roomsync.audit.repository;

import com.roomsync.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByActorUserId(Long actorUserId, Pageable pageable);

    Page<AuditLog> findAllByEntityTypeAndEntityId(String entityType, String entityId, Pageable pageable);

    @Query(value = """
        SELECT al FROM AuditLog al
        LEFT JOIN FETCH al.actorUser
        LEFT JOIN FETCH al.affectedUser
        LEFT JOIN FETCH al.location
        LEFT JOIN FETCH al.room
        LEFT JOIN FETCH al.booking
        WHERE (:actorUserId IS NULL OR al.actorUser.id = :actorUserId)
          AND (:action IS NULL OR al.action = :action)
          AND (:entityType IS NULL OR al.entityType = :entityType)
          AND (:locationId IS NULL OR al.location.id = :locationId)
          AND (:roomId IS NULL OR al.room.id = :roomId)
          AND (:bookingId IS NULL OR al.booking.id = :bookingId)
          AND (cast(:startDate as timestamp) IS NULL OR al.createdAt >= :startDate)
          AND (cast(:endDate as timestamp) IS NULL OR al.createdAt <= :endDate)
    """,
    countQuery = """
        SELECT COUNT(al) FROM AuditLog al
        WHERE (:actorUserId IS NULL OR al.actorUser.id = :actorUserId)
          AND (:action IS NULL OR al.action = :action)
          AND (:entityType IS NULL OR al.entityType = :entityType)
          AND (:locationId IS NULL OR al.location.id = :locationId)
          AND (:roomId IS NULL OR al.room.id = :roomId)
          AND (:bookingId IS NULL OR al.booking.id = :bookingId)
          AND (cast(:startDate as timestamp) IS NULL OR al.createdAt >= :startDate)
          AND (cast(:endDate as timestamp) IS NULL OR al.createdAt <= :endDate)
    """)
    Page<AuditLog> findAllByFilters(
            @Param("actorUserId") Long actorUserId,
            @Param("action") String action,
            @Param("entityType") String entityType,
            @Param("locationId") Long locationId,
            @Param("roomId") Long roomId,
            @Param("bookingId") Long bookingId,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            Pageable pageable
    );
}
