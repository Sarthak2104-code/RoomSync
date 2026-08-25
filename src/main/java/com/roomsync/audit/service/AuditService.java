package com.roomsync.audit.service;

import com.roomsync.audit.entity.AuditLog;
import com.roomsync.audit.repository.AuditLogRepository;
import com.roomsync.booking.entity.Booking;
import com.roomsync.user.entity.User;
import com.roomsync.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public AuditLog logBookingAction(
            String action,
            Booking booking,
            Long actorUserId,
            Map<String, Object> metadata) {

        User actorUser = null;
        if (actorUserId != null) {
            actorUser = userRepository.findById(actorUserId).orElse(null);
        }

        Map<String, Object> finalMetadata = new HashMap<>();
        if (metadata != null) {
            finalMetadata.putAll(metadata);
        }
        if (actorUserId == null) {
            finalMetadata.putIfAbsent("actorType", "SYSTEM");
        } else {
            finalMetadata.putIfAbsent("actorType", "USER");
        }

        AuditLog auditLog = AuditLog.builder()
                .actorUser(actorUser)
                .affectedUser(booking != null ? booking.getUser() : null)
                .action(action)
                .entityType("BOOKING")
                .entityId(booking != null ? booking.getId().toString() : null)
                .location(booking != null && booking.getRoom() != null ? booking.getRoom().getLocation() : null)
                .room(booking != null && booking.getRoom() != null ? booking.getRoom() : null)
                .booking(booking)
                .metadata(finalMetadata)
                .build();

        AuditLog saved = auditLogRepository.save(auditLog);
        log.info("Persisted AuditLog id: {} action: {} for booking: {}", saved.getId(), action, booking != null ? booking.getId() : null);
        return saved;
    }
}
