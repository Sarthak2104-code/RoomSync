package com.roomsync.booking.dto;

import com.roomsync.booking.entity.BookingSeries;
import com.roomsync.booking.entity.BookingSeriesStatus;
import com.roomsync.booking.entity.RecurrenceFrequency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringSeriesResponse {
    private Long id;
    private Long userId;
    private String seriesName;
    private RecurrenceFrequency frequency;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer occurrenceCount;
    private LocalTime startLocalTime;
    private LocalTime endLocalTime;
    private String timezone;
    private String daysOfWeek;
    private Integer dayOfMonth;
    private BookingSeriesStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<BookingResponse> bookings;
    private List<RecurringOccurrenceResult> occurrences;
    private Integer totalOccurrences;
    private Integer confirmedCount;
    private Integer conflictCount;
    private Integer skippedCount;

    public static RecurringSeriesResponse fromEntity(BookingSeries series, List<BookingResponse> bookings) {
        return RecurringSeriesResponse.builder()
                .id(series.getId())
                .userId(series.getUser().getId())
                .seriesName(series.getSeriesName())
                .frequency(series.getFrequency())
                .startDate(series.getStartDate())
                .endDate(series.getEndDate())
                .occurrenceCount(series.getOccurrenceCount())
                .startLocalTime(series.getStartLocalTime())
                .endLocalTime(series.getEndLocalTime())
                .timezone(series.getTimezone())
                .daysOfWeek(series.getDaysOfWeek())
                .dayOfMonth(series.getDayOfMonth())
                .status(series.getStatus())
                .createdAt(series.getCreatedAt())
                .updatedAt(series.getUpdatedAt())
                .bookings(bookings)
                .build();
    }
}
