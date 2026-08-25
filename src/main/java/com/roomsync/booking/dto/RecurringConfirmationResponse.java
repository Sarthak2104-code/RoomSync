package com.roomsync.booking.dto;

import com.roomsync.booking.entity.BookingSeriesStatus;
import com.roomsync.booking.entity.RecurrenceFrequency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringConfirmationResponse {
    private Long seriesId;
    private String seriesName;
    private Long roomId;
    private String roomName;
    private RecurrenceFrequency frequency;
    private BookingSeriesStatus seriesStatus;
    private Integer totalOccurrences;
    private Integer confirmedCount;
    private Integer conflictCount;
    private List<RecurringOccurrenceResult> occurrences;
}
