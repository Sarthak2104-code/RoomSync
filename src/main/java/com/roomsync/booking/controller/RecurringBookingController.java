package com.roomsync.booking.controller;

import com.roomsync.admin.dto.AdminRequestResponse;
import com.roomsync.booking.dto.BookingResponse;
import com.roomsync.booking.dto.ContactAdminOccurrenceRequest;
import com.roomsync.booking.dto.CreateRecurringBookingRequest;
import com.roomsync.booking.dto.RecurringConfirmationResponse;
import com.roomsync.booking.dto.RecurringOccurrencePreview;
import com.roomsync.booking.dto.RecurringOccurrenceResult;
import com.roomsync.booking.dto.RecurringPreviewResponse;
import com.roomsync.booking.dto.RecurringSeriesResponse;
import com.roomsync.booking.dto.ResolveAlternateRoomRequest;
import com.roomsync.booking.dto.SkipOccurrenceRequest;
import com.roomsync.booking.service.RecurringBookingService;
import com.roomsync.common.security.AuthenticatedUser;
import com.roomsync.common.security.UserContextHolder;
import com.roomsync.reliability.service.IdempotencyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/bookings/recurring")
@RequiredArgsConstructor
public class RecurringBookingController {

    private final RecurringBookingService recurringBookingService;
    private final IdempotencyService idempotencyService;
    private final HttpServletRequest httpRequest;

    @PostMapping("/preview")
    public ResponseEntity<RecurringPreviewResponse> previewSeries(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateRecurringBookingRequest request) {
        AuthenticatedUser user = resolveUser(currentUser);
        RecurringPreviewResponse response = recurringBookingService.previewSeries(user.id(), request);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<RecurringConfirmationResponse> createAndConfirmSeries(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateRecurringBookingRequest request) {
        AuthenticatedUser user = resolveUser(currentUser);
        RecurringConfirmationResponse response = recurringBookingService.createAndConfirmSeries(user.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecurringSeriesResponse> getSeries(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id) {
        AuthenticatedUser user = resolveUser(currentUser);
        RecurringSeriesResponse response = recurringBookingService.getSeries(id, user.id());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{seriesId}/occurrences/{occurrenceIndex}")
    public ResponseEntity<Void> cancelOccurrence(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long seriesId,
            @PathVariable Integer occurrenceIndex,
            @RequestParam(name = "reason", required = false) String reason) {
        AuthenticatedUser user = resolveUser(currentUser);
        recurringBookingService.cancelOccurrence(seriesId, occurrenceIndex, user.id(), reason);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{seriesId}/occurrences/{occurrenceIndex}/skip")
    public ResponseEntity<RecurringOccurrenceResult> skipOccurrence(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long seriesId,
            @PathVariable Integer occurrenceIndex,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody(required = false) SkipOccurrenceRequest request) {

        AuthenticatedUser user = resolveUser(currentUser);
        String reason = (request != null) ? request.getReason() : null;

        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            RecurringOccurrenceResult response = idempotencyService.executeIdempotent(
                    user.id(),
                    idempotencyKey,
                    "RECURRING_OCCURRENCE_SKIP",
                    "POST",
                    String.format("/api/bookings/recurring/%d/occurrences/%d/skip", seriesId, occurrenceIndex),
                    request != null ? request : Map.of(),
                    RecurringOccurrenceResult.class,
                    () -> recurringBookingService.skipOccurrence(seriesId, occurrenceIndex, user.id(), reason)
            );
            return ResponseEntity.ok(response);
        }

        RecurringOccurrenceResult response = recurringBookingService.skipOccurrence(
                seriesId,
                occurrenceIndex,
                user.id(),
                reason
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{seriesId}/occurrences/{occurrenceIndex}/alternate-room")
    public ResponseEntity<BookingResponse> resolveOccurrenceWithAlternateRoom(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long seriesId,
            @PathVariable Integer occurrenceIndex,
            @Valid @RequestBody ResolveAlternateRoomRequest request) {
        AuthenticatedUser user = resolveUser(currentUser);
        BookingResponse response = recurringBookingService.resolveOccurrenceWithAlternateRoom(
                seriesId,
                occurrenceIndex,
                user.id(),
                request.getAlternateRoomId()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{seriesId}/occurrences/{occurrenceIndex}/contact-admin")
    public ResponseEntity<AdminRequestResponse> contactAdminForOccurrence(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long seriesId,
            @PathVariable Integer occurrenceIndex,
            @Valid @RequestBody ContactAdminOccurrenceRequest request) {
        AuthenticatedUser user = resolveUser(currentUser);
        AdminRequestResponse response = recurringBookingService.contactAdminForOccurrence(
                seriesId,
                occurrenceIndex,
                user.id(),
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
        throw new IllegalStateException("Unauthenticated user attempting to access booking endpoint");
    }
}
