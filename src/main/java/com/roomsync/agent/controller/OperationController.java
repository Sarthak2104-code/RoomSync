package com.roomsync.agent.controller;

import com.roomsync.agent.dto.OperationResponse;
import com.roomsync.agent.service.OperationService;
import com.roomsync.common.response.PageResponse;
import com.roomsync.common.security.AuthenticatedUser;
import com.roomsync.security.exception.UnauthorizedException;
import com.roomsync.user.entity.User;
import com.roomsync.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operations")
@RequiredArgsConstructor
public class OperationController {

    private final OperationService operationService;
    private final UserRepository userRepository;

    @GetMapping("/{operationId}")
    public ResponseEntity<OperationResponse> getOperation(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable String operationId) {

        if (currentUser == null) {
            throw new UnauthorizedException("Authentication required");
        }

        User user = userRepository.findById(currentUser.id())
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));

        OperationResponse response = operationService.getOperation(operationId, user);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<OperationResponse>> getOperations(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        if (currentUser == null) {
            throw new UnauthorizedException("Authentication required");
        }

        User user = userRepository.findById(currentUser.id())
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));

        PageResponse<OperationResponse> response = operationService.getOperations(user, pageable);
        return ResponseEntity.ok(response);
    }
}
