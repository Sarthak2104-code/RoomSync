package com.roomsync.admin.dto;

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
public class RoomUtilizationResponse {
    private Long roomId;
    private String roomName;
    private Long locationId;
    private String locationName;
    private String locationTimezone;
    private OffsetDateTime reportingPeriodStart;
    private OffsetDateTime reportingPeriodEnd;
    private long totalAvailableMinutes;
    private long totalBookedMinutes;
    private double utilizationPercentage;
    private int bookingCount;
}
