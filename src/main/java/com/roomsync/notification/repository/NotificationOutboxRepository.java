package com.roomsync.notification.repository;

import com.roomsync.notification.entity.NotificationOutbox;
import com.roomsync.notification.entity.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    Optional<NotificationOutbox> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    List<NotificationOutbox> findAllByStatusAndNextAttemptTimeBefore(NotificationStatus status, OffsetDateTime time);

    Page<NotificationOutbox> findAllByRecipient(String recipient, Pageable pageable);

    @Query(value = """
        SELECT id FROM notification_outbox
        WHERE status IN ('PENDING', 'FAILED')
          AND (next_attempt_time IS NULL OR next_attempt_time <= :now)
        ORDER BY created_at ASC
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    List<Long> claimPendingNotificationIds(@Param("now") OffsetDateTime now, @Param("limit") int limit);
}
