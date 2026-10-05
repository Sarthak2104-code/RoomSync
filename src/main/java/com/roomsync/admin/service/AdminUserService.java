package com.roomsync.admin.service;

import com.roomsync.admin.dto.AdminUserDetailResponse;
import com.roomsync.admin.dto.AdminUserSummaryResponse;
import com.roomsync.admin.dto.UpdateUserBookingAccessRequest;
import com.roomsync.audit.service.AuditService;
import com.roomsync.common.response.PageResponse;
import com.roomsync.user.entity.User;
import com.roomsync.admin.exception.AdminSelfBookingActionException;
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

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<AdminUserSummaryResponse> getAdminUsers(
            String search,
            Long locationId,
            Boolean bookingEnabled,
            Boolean active,
            String role,
            Pageable pageable) {

        Pageable sanitized = sanitizePageable(pageable);
        String searchPattern = (search != null && !search.trim().isEmpty())
                ? "%" + search.trim().toLowerCase() + "%"
                : null;
        String trimmedRole = (role != null && !role.trim().isEmpty())
                ? role.trim().toUpperCase()
                : null;

        Page<User> page = userRepository.findAllByAdminFilters(
                searchPattern, locationId, bookingEnabled, active, trimmedRole, sanitized);

        return PageResponse.fromPage(page, AdminUserSummaryResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse getAdminUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return AdminUserDetailResponse.fromEntity(user);
    }

    @Transactional
    public AdminUserDetailResponse updateUserBookingAccess(
            Long userId,
            Long adminUserId,
            UpdateUserBookingAccessRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Verify admin exists
        if (adminUserId != null && !userRepository.existsById(adminUserId)) {
            throw new UserNotFoundException(adminUserId);
        }

        // Prevent admin self-modification of booking access
        if (adminUserId != null && adminUserId.equals(userId)) {
            throw new AdminSelfBookingActionException("Administrators cannot modify their own booking access permissions.");
        }

        boolean previousState = user.isBookingEnabled();
        boolean newState = request.getBookingEnabled();

        user.setBookingEnabled(newState);
        User savedUser = userRepository.save(user);

        String action = newState ? "USER_BOOKING_UNBLOCKED" : "USER_BOOKING_BLOCKED";

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("previousBookingEnabled", previousState);
        metadata.put("newBookingEnabled", newState);
        if (request.getReason() != null && !request.getReason().trim().isEmpty()) {
            metadata.put("reason", request.getReason().trim());
        }

        if (auditService != null) {
            auditService.logUserAction(action, savedUser, adminUserId, metadata);
        }

        log.info("Updated booking access for user id: {} (wissenId: {}) to bookingEnabled: {} by admin: {}",
                userId, savedUser.getWissenId(), newState, adminUserId);

        return AdminUserDetailResponse.fromEntity(savedUser);
    }

    private Pageable sanitizePageable(Pageable pageable) {
        int page = Math.max(pageable.getPageNumber(), 0);
        int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE);

        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Direction.ASC, "name");
        }

        return PageRequest.of(page, size, sort);
    }
}
