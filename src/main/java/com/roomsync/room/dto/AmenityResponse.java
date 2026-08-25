package com.roomsync.room.dto;

import com.roomsync.room.entity.Amenity;
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
public class AmenityResponse {

    private Long id;
    private String name;
    private String icon;
    private String description;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static AmenityResponse fromEntity(Amenity entity) {
        if (entity == null) return null;
        return AmenityResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .icon(entity.getIcon())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
