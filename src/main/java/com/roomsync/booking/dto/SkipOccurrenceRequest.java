package com.roomsync.booking.dto;

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
public class SkipOccurrenceRequest {

    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    private String reason;
}
