package com.roomsync.admin.dto;

import com.roomsync.admin.entity.AdminRequestStatus;
import jakarta.validation.constraints.NotNull;
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
public class ResolveAdminRequestRequest {

    @NotNull(message = "Status is required")
    private AdminRequestStatus status;

    private String message;
    private String resolutionNotes;
}
