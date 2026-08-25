package com.roomsync.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAmenityRequest {

    @NotBlank(message = "Amenity name is required")
    @Size(max = 100, message = "Amenity name cannot exceed 100 characters")
    private String name;

    @Size(max = 100, message = "Icon cannot exceed 100 characters")
    private String icon;

    private String description;
}
