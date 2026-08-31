package com.roomsync.admin.dto;

import com.roomsync.booking.entity.Booking;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurrentBookingSummary {
    private Long bookingId;
    private Long userId;
    private String userWissenId;
    private String userName;
    private String userEmail;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private String reason;

    public static CurrentBookingSummary fromEntity(Booking booking) {
        if (booking == null) return null;
        return CurrentBookingSummary.builder()
                .bookingId(booking.getId())
                .userId(booking.getUser() != null ? booking.getUser().getId() : null)
                .userWissenId(booking.getUser() != null ? booking.getUser().getWissenId() : null)
                .userName(booking.getUser() != null ? booking.getUser().getName() : null)
                .userEmail(booking.getUser() != null ? booking.getUser().getEmail() : null)
                .startTime(booking.getStartTime())
                .endTime(booking.getEndTime())
                .reason(booking.getReason())
                .build();
    }
}
