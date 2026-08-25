package com.roomsync.admin.dto;

import com.roomsync.room.entity.RoomStatus;
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
public class RoomOccupancyResponse {
    private Long roomId;
    private String roomName;
    private Integer capacity;
    private Long locationId;
    private String locationName;
    private String locationTimezone;
    private RoomStatus administrativeState;
    private boolean active;
    private OccupancyStatus occupancyStatus;
    private CurrentBookingSummary currentBooking;
}
