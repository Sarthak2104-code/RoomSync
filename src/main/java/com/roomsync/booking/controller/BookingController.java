package com.roomsync.booking.controller;

import com.roomsync.booking.dto.BookingResponse;
import com.roomsync.booking.dto.CreateBookingRequest;
import com.roomsync.booking.dto.RescheduleBookingRequest;
import com.roomsync.booking.service.BookingService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final HttpServletRequest httpRequest;

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateBookingRequest request) {
        AuthenticatedUser user = resolveUser(currentUser);
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

    @PutMapping("/{id}")
    public ResponseEntity<BookingResponse> rescheduleBooking(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody RescheduleBookingRequest request) {
        AuthenticatedUser user = resolveUser(currentUser);
        BookingResponse response = bookingService.rescheduleBooking(id, user.id(), request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelBooking(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id) {
        AuthenticatedUser user = resolveUser(currentUser);
        bookingService.cancelBooking(id, user.id());
        return ResponseEntity.noContent().build();
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
