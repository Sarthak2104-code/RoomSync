package com.roomsync.admin.service;

import com.roomsync.admin.dto.AdminRequestResponse;
import com.roomsync.admin.dto.ResolveAdminRequestRequest;
import com.roomsync.admin.entity.AdminRequest;
import com.roomsync.admin.entity.AdminRequestStatus;
import com.roomsync.admin.exception.AdminRequestNotFoundException;
import com.roomsync.admin.repository.AdminRequestRepository;
import com.roomsync.audit.service.AuditService;
import com.roomsync.common.response.PageResponse;
import com.roomsync.user.entity.User;
import com.roomsync.user.exception.UserNotFoundException;
import com.roomsync.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminRequestService {

    private final AdminRequestRepository adminRequestRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<AdminRequestResponse> getAdminRequests(
            AdminRequestStatus status,
            Long locationId,
            Pageable pageable) {

        Page<AdminRequest> page = adminRequestRepository.findAllByFilters(status, locationId, pageable);
        return PageResponse.fromPage(page, AdminRequestResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public AdminRequestResponse getAdminRequestById(Long id) {
        AdminRequest request = adminRequestRepository.findById(id)
                .orElseThrow(() -> new AdminRequestNotFoundException(id));
        return AdminRequestResponse.fromEntity(request);
    }

    @Transactional
    public AdminRequestResponse resolveAdminRequest(
            Long id,
            Long adminUserId,
            ResolveAdminRequestRequest requestDto) {

        AdminRequest adminRequest = adminRequestRepository.findById(id)
                .orElseThrow(() -> new AdminRequestNotFoundException(id));

        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new UserNotFoundException(adminUserId));

        adminRequest.setStatus(requestDto.getStatus());
        adminRequest.setResolvedByUser(adminUser);

        if (requestDto.getResolutionNotes() != null && !requestDto.getResolutionNotes().trim().isEmpty()) {
            adminRequest.setMessage(adminRequest.getMessage() + " | Resolution: " + requestDto.getResolutionNotes().trim());
        } else if (requestDto.getMessage() != null && !requestDto.getMessage().trim().isEmpty()) {
            adminRequest.setMessage(adminRequest.getMessage() + " | Resolution: " + requestDto.getMessage().trim());
        }

        AdminRequest saved = adminRequestRepository.save(adminRequest);
        log.info("Resolved AdminRequest id: {} to status: {} by admin: {}", id, saved.getStatus(), adminUserId);

        if (auditService != null) {
            auditService.logAdminRequestAction(
                    "ADMIN_REQUEST_RESOLVED",
                    saved,
                    adminUserId,
                    Map.of("status", saved.getStatus().name())
            );
        }

        return AdminRequestResponse.fromEntity(saved);
    }
}
