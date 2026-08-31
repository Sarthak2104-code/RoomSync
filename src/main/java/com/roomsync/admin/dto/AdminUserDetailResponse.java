package com.roomsync.admin.dto;

import com.roomsync.location.entity.Location;
import com.roomsync.user.entity.User;
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
public class AdminUserDetailResponse {

    private Long id;
    private String wissenId;
    private String name;
    private String email;
    private String role;
    private LocationSummary location;
    private boolean active;
    private boolean bookingEnabled;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LocationSummary {
        private Long id;
        private String name;
        private String code;
        private String timezone;
    }

    public static AdminUserDetailResponse fromEntity(User user) {
        if (user == null) {
            return null;
        }

        LocationSummary locSummary = null;
        Location loc = user.getLocation();
        if (loc != null) {
            locSummary = LocationSummary.builder()
                    .id(loc.getId())
                    .name(loc.getName())
                    .code(loc.getCode())
                    .timezone(loc.getTimezone())
                    .build();
        }

        String roleName = user.getRole() != null ? user.getRole().getName() : "USER";

        return AdminUserDetailResponse.builder()
                .id(user.getId())
                .wissenId(user.getWissenId())
                .name(user.getName())
                .email(user.getEmail())
                .role(roleName)
                .location(locSummary)
                .active(user.isActive())
                .bookingEnabled(user.isBookingEnabled())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
