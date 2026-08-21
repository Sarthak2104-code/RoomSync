package com.roomsync.audit.repository;

import com.roomsync.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findAllByActorUserId(Long actorUserId, Pageable pageable);
    Page<AuditLog> findAllByEntityTypeAndEntityId(String entityType, String entityId, Pageable pageable);
}
