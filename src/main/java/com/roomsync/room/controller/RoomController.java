package com.roomsync.room.controller;

import com.roomsync.common.response.PageResponse;
import com.roomsync.common.security.AuthenticatedUser;
import com.roomsync.common.security.UserContextHolder;
import com.roomsync.room.dto.CreateRoomRequest;
import com.roomsync.room.dto.RoomResponse;
import com.roomsync.room.dto.UpdateRoomRequest;
import com.roomsync.room.service.RoomService;
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
import org.springframework.security.access.prepost.PreAuthorize;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final HttpServletRequest httpRequest;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomResponse> createRoom(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateRoomRequest request) {
        AuthenticatedUser user = resolveUser(currentUser);
        RoomResponse response = roomService.createRoom(user.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<RoomResponse>> getRooms(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) Long locationId,
            @PageableDefault(page = 0, size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        AuthenticatedUser user = resolveUser(currentUser);
        PageResponse<RoomResponse> response = roomService.getRooms(user.id(), locationId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomResponse> getRoomById(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id) {
        AuthenticatedUser user = resolveUser(currentUser);
        RoomResponse response = roomService.getRoomById(user.id(), id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomResponse> updateRoom(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoomRequest request) {
        AuthenticatedUser user = resolveUser(currentUser);
        RoomResponse response = roomService.updateRoom(user.id(), id, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/lock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomResponse> lockRoom(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id) {
        AuthenticatedUser user = resolveUser(currentUser);
        RoomResponse response = roomService.lockRoom(user.id(), id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomResponse> unlockRoom(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id) {
        AuthenticatedUser user = resolveUser(currentUser);
        RoomResponse response = roomService.unlockRoom(user.id(), id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateRoom(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id) {
        AuthenticatedUser user = resolveUser(currentUser);
        roomService.deactivateRoom(user.id(), id);
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
