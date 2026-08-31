package com.roomsync.admin.dto;

import jakarta.validation.constraints.NotNull;
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
public class UpdateUserBookingAccessRequest {

    @NotNull(message = "bookingEnabled flag is required")
    private Boolean bookingEnabled;

    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
