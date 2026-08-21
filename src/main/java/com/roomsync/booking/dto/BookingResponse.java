package com.roomsync.booking.dto;

import com.roomsync.booking.entity.Booking;
import com.roomsync.booking.entity.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Clock;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingResponse {

    private Long id;
    private Long roomId;
    private String roomName;
    private Long userId;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private BookingStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static BookingResponse fromEntity(Booking booking, Clock clock) {
        if (booking == null) {
            return null;
        }

        BookingStatus effectiveStatus = computeEffectiveStatus(booking, clock);

        return BookingResponse.builder()
                .id(booking.getId())
                .roomId(booking.getRoom() != null ? booking.getRoom().getId() : null)
                .roomName(booking.getRoom() != null ? booking.getRoom().getName() : null)
                .userId(booking.getUser() != null ? booking.getUser().getId() : null)
                .startTime(booking.getStartTime())
                .endTime(booking.getEndTime())
                .status(effectiveStatus)
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }

    private static BookingStatus computeEffectiveStatus(Booking booking, Clock clock) {
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            OffsetDateTime now = OffsetDateTime.now(clock);
            if (booking.getEndTime().isBefore(now)) {
                return BookingStatus.COMPLETED;
            }
        }
        return booking.getStatus();
    }
}
