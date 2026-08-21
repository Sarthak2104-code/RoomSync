package com.roomsync.room.dto;

import com.roomsync.location.dto.LocationResponse;
import com.roomsync.room.entity.Room;
import com.roomsync.room.entity.RoomStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomResponse {

    private Long id;
    private String name;
    private Integer capacity;
    private LocationResponse location;
    private String description;
    private RoomStatus status;
    private boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static RoomResponse fromEntity(Room room) {
        if (room == null) {
            return null;
        }
        return RoomResponse.builder()
                .id(room.getId())
                .name(room.getName())
                .capacity(room.getCapacity())
                .location(LocationResponse.fromEntity(room.getLocation()))
                .description(room.getDescription())
                .status(room.getStatus())
                .active(room.isActive())
                .createdAt(room.getCreatedAt())
                .updatedAt(room.getUpdatedAt())
                .build();
    }
}
