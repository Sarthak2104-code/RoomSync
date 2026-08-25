package com.roomsync.admin.dto;

import com.roomsync.admin.entity.AdminRequest;
import com.roomsync.admin.entity.AdminRequestStatus;
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
public class AdminRequestResponse {
    private Long id;
    private Long requesterUserId;
    private Long locationId;
    private Long roomId;
    private Long bookingSeriesId;
    private Long bookingId;
    private String requestType;
    private String message;
    private AdminRequestStatus status;
    private Long resolvedByUserId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static AdminRequestResponse fromEntity(AdminRequest entity) {
        return AdminRequestResponse.builder()
                .id(entity.getId())
                .requesterUserId(entity.getRequesterUser() != null ? entity.getRequesterUser().getId() : null)
                .locationId(entity.getLocation() != null ? entity.getLocation().getId() : null)
                .roomId(entity.getRoom() != null ? entity.getRoom().getId() : null)
                .bookingSeriesId(entity.getBookingSeries() != null ? entity.getBookingSeries().getId() : null)
                .bookingId(entity.getBooking() != null ? entity.getBooking().getId() : null)
                .requestType(entity.getRequestType())
                .message(entity.getMessage())
                .status(entity.getStatus())
                .resolvedByUserId(entity.getResolvedByUser() != null ? entity.getResolvedByUser().getId() : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
