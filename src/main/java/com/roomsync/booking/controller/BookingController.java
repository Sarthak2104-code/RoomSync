package com.roomsync.booking.controller;

import com.roomsync.booking.dto.BookingResponse;
import com.roomsync.booking.dto.CancelBookingRequest;
import com.roomsync.booking.dto.CreateBookingRequest;
import com.roomsync.booking.dto.RescheduleBookingRequest;
import com.roomsync.booking.service.BookingService;
import com.roomsync.common.response.PageResponse;
import com.roomsync.common.security.AuthenticatedUser;
import com.roomsync.common.security.UserContextHolder;
import com.roomsync.reliability.service.IdempotencyService;
import com.roomsync.security.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final IdempotencyService idempotencyService;
    private final HttpServletRequest httpRequest;

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateBookingRequest request) {

        AuthenticatedUser user = resolveUser(currentUser);

        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            BookingResponse response = idempotencyService.executeIdempotent(
                    user.id(),
                    idempotencyKey,
                    "BOOKING_CREATE",
                    "POST",
                    "/api/bookings",
                    request,
                    BookingResponse.class,
                    () -> bookingService.createBooking(user.id(), request)
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        BookingResponse response = bookingService.createBooking(user.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBooking(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id) {
        AuthenticatedUser user = resolveUser(currentUser);
        BookingResponse response = bookingService.getBooking(id, user.id());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my")
    public ResponseEntity<PageResponse<BookingResponse>> getMyBookings(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PageableDefault(page = 0, size = 10, sort = "startTime", direction = Sort.Direction.DESC) Pageable pageable) {
        AuthenticatedUser user = resolveUser(currentUser);
        PageResponse<BookingResponse> response = bookingService.getMyBookings(user.id(), pageable);
        return ResponseEntity.ok(response);
    }

    @PutMapping({"/{id}", "/{id}/reschedule"})
    public ResponseEntity<BookingResponse> rescheduleBooking(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RescheduleBookingRequest request) {

        AuthenticatedUser user = resolveUser(currentUser);

        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            BookingResponse response = idempotencyService.executeIdempotent(
                    user.id(),
                    idempotencyKey,
                    "BOOKING_RESCHEDULE",
                    "PUT",
                    "/api/bookings/" + id + "/reschedule",
                    request,
                    BookingResponse.class,
                    () -> bookingService.rescheduleBooking(id, user.id(), request)
            );
            return ResponseEntity.ok(response);
        }

        BookingResponse response = bookingService.rescheduleBooking(id, user.id(), request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping({"/{id}", "/{id}/reschedule"})
    public ResponseEntity<BookingResponse> rescheduleBookingPatch(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RescheduleBookingRequest request) {
        return rescheduleBooking(currentUser, id, idempotencyKey, request);
    }

    @PostMapping("/{id}/reschedule")
    public ResponseEntity<BookingResponse> rescheduleBookingPost(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RescheduleBookingRequest request) {
        return rescheduleBooking(currentUser, id, idempotencyKey, request);
    }

    @DeleteMapping({"/{id}", "/{id}/cancel"})
    public ResponseEntity<Void> cancelBooking(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam(name = "reason", required = false) String paramReason,
            @Valid @RequestBody(required = false) CancelBookingRequest request) {

        AuthenticatedUser user = resolveUser(currentUser);
        String reason = (request != null && request.getReason() != null) ? request.getReason() : paramReason;

        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            idempotencyService.executeIdempotent(
                    user.id(),
                    idempotencyKey,
                    "BOOKING_CANCEL",
                    "DELETE",
                    "/api/bookings/" + id,
                    Map.of("reason", reason != null ? reason : ""),
                    Void.class,
                    () -> {
                        bookingService.cancelBooking(id, user.id(), reason);
                        return null;
                    }
            );
            return ResponseEntity.noContent().build();
        }

        bookingService.cancelBooking(id, user.id(), reason);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelBookingPatch(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam(name = "reason", required = false) String paramReason,
            @Valid @RequestBody(required = false) CancelBookingRequest request) {
        return cancelBooking(currentUser, id, idempotencyKey, paramReason, request);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelBookingPost(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam(name = "reason", required = false) String paramReason,
            @Valid @RequestBody(required = false) CancelBookingRequest request) {
        return cancelBooking(currentUser, id, idempotencyKey, paramReason, request);
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
