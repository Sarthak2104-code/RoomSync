package com.roomsync.location.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
public class CreateLocationRequest {

    @NotBlank(message = "Location name is required")
    @Size(min = 2, max = 100, message = "Location name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Location code is required")
    @Size(min = 2, max = 20, message = "Location code must be between 2 and 20 characters")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Location code must contain only alphanumeric characters, underscores, or hyphens")
    private String code;
}
