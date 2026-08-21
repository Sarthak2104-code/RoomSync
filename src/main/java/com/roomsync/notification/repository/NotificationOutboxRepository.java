package com.roomsync.notification.repository;

import com.roomsync.notification.entity.NotificationOutbox;
import com.roomsync.notification.entity.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {
    List<NotificationOutbox> findAllByStatusAndNextAttemptTimeBefore(NotificationStatus status, OffsetDateTime time);
    Page<NotificationOutbox> findAllByRecipient(String recipient, Pageable pageable);
}
