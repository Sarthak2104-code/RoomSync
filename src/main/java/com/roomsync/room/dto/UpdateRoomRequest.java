package com.roomsync.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class UpdateRoomRequest {

    @NotBlank(message = "Room name is required and cannot be blank")
    @Size(max = 255, message = "Room name cannot exceed 255 characters")
    private String name;

    @NotNull(message = "Capacity is required")
    @Positive(message = "Capacity must be a positive integer greater than zero")
    private Integer capacity;

    private String description;
}
