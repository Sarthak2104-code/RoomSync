package com.roomsync.admin.controller;

import com.roomsync.admin.dto.AdminRequestResponse;
import com.roomsync.admin.dto.ResolveAdminRequestRequest;
import com.roomsync.admin.dto.RoomOccupancyResponse;
import com.roomsync.admin.dto.UtilizationReportResponse;
import com.roomsync.admin.entity.AdminRequestStatus;
import com.roomsync.admin.service.AdminRequestService;
import com.roomsync.admin.service.AdminService;
import com.roomsync.admin.service.AnalyticsService;
import com.roomsync.admin.service.OccupancyService;
import com.roomsync.booking.dto.BookingResponse;
import com.roomsync.booking.entity.BookingStatus;
import com.roomsync.common.response.PageResponse;
import com.roomsync.common.security.AuthenticatedUser;
import com.roomsync.common.security.UserContextHolder;
import com.roomsync.security.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.roomsync.admin.dto.AdminUserDetailResponse;
import com.roomsync.admin.dto.AdminUserSummaryResponse;
import com.roomsync.admin.dto.UpdateUserBookingAccessRequest;
import com.roomsync.admin.service.AdminUserService;
import com.roomsync.audit.dto.AuditLogResponse;
import com.roomsync.audit.service.AuditService;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final OccupancyService occupancyService;
    private final AnalyticsService analyticsService;
    private final AdminService adminService;
    private final AdminRequestService adminRequestService;
    private final AdminUserService adminUserService;
    private final AuditService auditService;
    private final HttpServletRequest httpRequest;

    @GetMapping("/users")
    public ResponseEntity<PageResponse<AdminUserSummaryResponse>> getAdminUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Boolean bookingEnabled,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String role,
            @PageableDefault(page = 0, size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {

        PageResponse<AdminUserSummaryResponse> response = adminUserService.getAdminUsers(
                search, locationId, bookingEnabled, active, role, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<AdminUserDetailResponse> getAdminUserById(@PathVariable Long id) {
        AdminUserDetailResponse response = adminUserService.getAdminUserById(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/users/{id}/booking-access")
    public ResponseEntity<AdminUserDetailResponse> updateUserBookingAccess(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody UpdateUserBookingAccessRequest request) {

        AuthenticatedUser admin = resolveUser(currentUser);
        AdminUserDetailResponse response = adminUserService.updateUserBookingAccess(id, admin.id(), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<PageResponse<AuditLogResponse>> getAuditLogs(
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long bookingId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        PageResponse<AuditLogResponse> response = auditService.getAuditLogs(
                actorUserId, action, entityType, locationId, roomId, bookingId, startDate, endDate, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/occupancy")
    public ResponseEntity<PageResponse<RoomOccupancyResponse>> getOccupancy(
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Long roomId,
            @PageableDefault(page = 0, size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {

        PageResponse<RoomOccupancyResponse> response = occupancyService.getOccupancy(locationId, roomId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/utilization")
    public ResponseEntity<UtilizationReportResponse> getUtilization(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Long roomId) {

        UtilizationReportResponse response = analyticsService.getUtilization(startDate, endDate, locationId, roomId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/bookings")
    public ResponseEntity<PageResponse<BookingResponse>> getAdminBookings(
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(page = 0, size = 10, sort = "startTime", direction = Sort.Direction.DESC) Pageable pageable) {

        PageResponse<BookingResponse> response = adminService.getAdminBookings(locationId, roomId, userId, status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/bookings/{id}")
    public ResponseEntity<BookingResponse> getAdminBookingById(@PathVariable Long id) {
        BookingResponse response = adminService.getAdminBookingById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/requests")
    public ResponseEntity<PageResponse<AdminRequestResponse>> getAdminRequests(
            @RequestParam(required = false) AdminRequestStatus status,
            @RequestParam(required = false) Long locationId,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        PageResponse<AdminRequestResponse> response = adminRequestService.getAdminRequests(status, locationId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/requests/{id}")
    public ResponseEntity<AdminRequestResponse> getAdminRequestById(@PathVariable Long id) {
        AdminRequestResponse response = adminRequestService.getAdminRequestById(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/requests/{id}")
    public ResponseEntity<AdminRequestResponse> patchAdminRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody ResolveAdminRequestRequest request) {

        AuthenticatedUser admin = resolveUser(currentUser);
        AdminRequestResponse response = adminRequestService.resolveAdminRequest(id, admin.id(), request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/requests/{id}")
    public ResponseEntity<AdminRequestResponse> putAdminRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody ResolveAdminRequestRequest request) {

        AuthenticatedUser admin = resolveUser(currentUser);
        AdminRequestResponse response = adminRequestService.resolveAdminRequest(id, admin.id(), request);
        return ResponseEntity.ok(response);
    }

    private AuthenticatedUser resolveUser(AuthenticatedUser currentUser) {
        if (currentUser != null) {
            return currentUser;
        }
        if (UserContextHolder.get().isPresent()) {
            return UserContextHolder.get().get();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        if (httpRequest != null) {
            Object secObj = httpRequest.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if (secObj == null) {
                HttpSession session = httpRequest.getSession(false);
                if (session != null) {
                    secObj = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
                }
            }
            if (secObj instanceof SecurityContext sc && sc.getAuthentication() != null) {
                if (sc.getAuthentication().getPrincipal() instanceof AuthenticatedUser user) {
                    return user;
                }
            }
        }
        throw new UnauthorizedException("Authentication required");
    }
}
