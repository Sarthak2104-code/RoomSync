package com.roomsync.room.controller;

import com.roomsync.common.response.PageResponse;
import com.roomsync.room.dto.CreateRoomRequest;
import com.roomsync.room.dto.RoomResponse;
import com.roomsync.room.dto.UpdateRoomRequest;
import com.roomsync.room.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CreateRoomRequest request) {
        RoomResponse response = roomService.createRoom(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<RoomResponse>> getRooms(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) Long locationId,
            @PageableDefault(page = 0, size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        PageResponse<RoomResponse> response = roomService.getRooms(userId, locationId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomResponse> getRoomById(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        RoomResponse response = roomService.getRoomById(userId, id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoomResponse> updateRoom(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoomRequest request) {
        RoomResponse response = roomService.updateRoom(userId, id, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/lock")
    public ResponseEntity<RoomResponse> lockRoom(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        RoomResponse response = roomService.lockRoom(userId, id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/unlock")
    public ResponseEntity<RoomResponse> unlockRoom(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        RoomResponse response = roomService.unlockRoom(userId, id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateRoom(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        roomService.deactivateRoom(userId, id);
        return ResponseEntity.noContent().build();
    }
}
