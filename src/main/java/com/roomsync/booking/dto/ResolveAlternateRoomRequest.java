package com.roomsync.booking.dto;

import jakarta.validation.constraints.NotNull;
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
public class ResolveAlternateRoomRequest {

    @NotNull(message = "Alternate Room ID is required")
    private Long alternateRoomId;
}
