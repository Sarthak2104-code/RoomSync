package com.roomsync.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UtilizationReportResponse {
    private LocalDate startDate;
    private LocalDate endDate;
    private List<RoomUtilizationResponse> rooms;
    private double overallUtilizationPercentage;
}
