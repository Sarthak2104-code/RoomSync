package com.roomsync.location.dto;

import com.roomsync.location.entity.Location;
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
public class LocationResponse {

    private Long id;
    private String name;
    private String code;
    private boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static LocationResponse fromEntity(Location location) {
        if (location == null) {
            return null;
        }

        return LocationResponse.builder()
                .id(location.getId())
                .name(location.getName())
                .code(location.getCode())
                .active(location.isActive())
                .createdAt(location.getCreatedAt())
                .updatedAt(location.getUpdatedAt())
                .build();
    }
}
