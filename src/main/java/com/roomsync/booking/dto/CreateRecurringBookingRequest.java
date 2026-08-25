package com.roomsync.booking.dto;

import com.roomsync.booking.entity.RecurrenceFrequency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateRecurringBookingRequest {

    @NotNull(message = "Room ID is required")
    private Long roomId;

    @Size(max = 255, message = "Series name cannot exceed 255 characters")
    private String seriesName;

    @NotNull(message = "Frequency is required")
    private RecurrenceFrequency frequency;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    private LocalDate endDate;

    private Integer occurrenceCount;

    @NotNull(message = "Start local time is required")
    private LocalTime startLocalTime;

    @NotNull(message = "End local time is required")
    private LocalTime endLocalTime;

    private List<String> daysOfWeek;

    private Integer dayOfMonth;

    @NotBlank(message = "Reason is required")
    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    private String reason;
}
