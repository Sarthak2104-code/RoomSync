package com.roomsync.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
public class LoginRequest {

    @NotBlank(message = "Wissen ID is required")
    @Pattern(regexp = "^(?i)(WT|WI)[0-9]+$", message = "Wissen ID must start with WT or WI followed by digits")
    private String wissenId;

    @NotBlank(message = "Password is required")
    private String password;
}
