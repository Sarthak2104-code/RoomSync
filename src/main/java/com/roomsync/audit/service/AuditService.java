package com.roomsync.audit.service;

import com.roomsync.admin.entity.AdminRequest;
import com.roomsync.audit.dto.AuditLogResponse;
import com.roomsync.audit.entity.AuditLog;
import com.roomsync.audit.repository.AuditLogRepository;
import com.roomsync.booking.entity.Booking;
import com.roomsync.common.response.PageResponse;
import com.roomsync.location.entity.Location;
import com.roomsync.room.entity.Room;
import com.roomsync.user.entity.User;
import com.roomsync.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getAuditLogs(
            Long actorUserId,
            String action,
            String entityType,
            Long locationId,
            Long roomId,
            Long bookingId,
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            Pageable pageable) {

        Pageable sanitizedPageable = sanitizePageable(pageable);
        Page<AuditLog> page = auditLogRepository.findAllByFilters(
                actorUserId, action, entityType, locationId, roomId, bookingId, startDate, endDate, sanitizedPageable);

        return PageResponse.fromPage(page, AuditLogResponse::fromEntity);
    }

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
                .entityId(booking != null && booking.getId() != null ? booking.getId().toString() : null)
                .location(booking != null && booking.getRoom() != null ? booking.getRoom().getLocation() : null)
                .room(booking != null && booking.getRoom() != null ? booking.getRoom() : null)
                .booking(booking)
                .metadata(finalMetadata)
                .build();

        AuditLog saved = auditLogRepository.save(auditLog);
        log.info("Persisted AuditLog id: {} action: {} for booking: {}", saved.getId(), action, booking != null ? booking.getId() : null);
        return saved;
    }

    @Transactional
    public AuditLog logRoomAction(
            String action,
            Room room,
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
                .action(action)
                .entityType("ROOM")
                .entityId(room != null && room.getId() != null ? room.getId().toString() : null)
                .location(room != null ? room.getLocation() : null)
                .room(room)
                .metadata(finalMetadata)
                .build();

        AuditLog saved = auditLogRepository.save(auditLog);
        log.info("Persisted AuditLog id: {} action: {} for room: {}", saved.getId(), action, room != null ? room.getId() : null);
        return saved;
    }

    @Transactional
    public AuditLog logLocationAction(
            String action,
            Location location,
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
                .action(action)
                .entityType("LOCATION")
                .entityId(location != null && location.getId() != null ? location.getId().toString() : null)
                .location(location)
                .metadata(finalMetadata)
                .build();

        AuditLog saved = auditLogRepository.save(auditLog);
        log.info("Persisted AuditLog id: {} action: {} for location: {}", saved.getId(), action, location != null ? location.getId() : null);
        return saved;
    }

    @Transactional
    public AuditLog logAdminRequestAction(
            String action,
            AdminRequest request,
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
                .affectedUser(request != null ? request.getRequesterUser() : null)
                .action(action)
                .entityType("ADMIN_REQUEST")
                .entityId(request != null && request.getId() != null ? request.getId().toString() : null)
                .location(request != null ? request.getLocation() : null)
                .room(request != null ? request.getRoom() : null)
                .booking(request != null ? request.getBooking() : null)
                .metadata(finalMetadata)
                .build();

        AuditLog saved = auditLogRepository.save(auditLog);
        log.info("Persisted AuditLog id: {} action: {} for admin request: {}", saved.getId(), action, request != null ? request.getId() : null);
        return saved;
    }

    @Transactional
    public AuditLog logUserAction(
            String action,
            User affectedUser,
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
                .affectedUser(affectedUser)
                .action(action)
                .entityType("USER")
                .entityId(affectedUser != null && affectedUser.getId() != null ? affectedUser.getId().toString() : null)
                .location(affectedUser != null ? affectedUser.getLocation() : null)
                .metadata(finalMetadata)
                .build();

        AuditLog saved = auditLogRepository.save(auditLog);
        log.info("Persisted AuditLog id: {} action: {} for user: {}", saved.getId(), action, affectedUser != null ? affectedUser.getId() : null);
        return saved;
    }

    private Pageable sanitizePageable(Pageable pageable) {
        int page = Math.max(pageable.getPageNumber(), 0);
        int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE);

        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Direction.DESC, "createdAt");
        }

        return PageRequest.of(page, size, sort);
    }
}
