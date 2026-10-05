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
public class RecurringOccurrenceResult {
    private Integer occurrenceIndex;
    private LocalDate date;
    private Long bookingId;
    private String status;
    private Long roomId;
    private String roomName;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private String reason;
    private String conflictReason;
    private String operationId;
}
