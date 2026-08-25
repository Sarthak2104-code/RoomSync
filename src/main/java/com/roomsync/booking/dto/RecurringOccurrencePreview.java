package com.roomsync.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringOccurrencePreview {
    private Integer occurrenceIndex;
    private LocalDate date;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private String availability;
    private String conflictReason;
}
