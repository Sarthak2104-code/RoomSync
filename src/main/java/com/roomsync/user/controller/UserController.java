package com.roomsync.user.controller;

import com.roomsync.common.security.AuthenticatedUser;
import com.roomsync.common.security.UserContextHolder;
import com.roomsync.security.exception.UnauthorizedException;
import com.roomsync.user.dto.UserProfileResponse;
import com.roomsync.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing user profile operations.
 * Resolves caller identity exclusively from the validated security principal.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    /**
     * Retrieves the current authenticated user's authoritative profile.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserProfileResponse> getCurrentUserProfile(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        AuthenticatedUser user = resolveUser(currentUser);
        UserProfileResponse profile = userService.getCurrentUserProfile(user.id());
        return ResponseEntity.ok(profile);
    }

    private AuthenticatedUser resolveUser(AuthenticatedUser currentUser) {
        if (currentUser != null) {
            return currentUser;
        }
        if (UserContextHolder.get().isPresent()) {
            return UserContextHolder.get().get();
        }
        throw new UnauthorizedException("Full authentication is required to access this resource");
    }
}
