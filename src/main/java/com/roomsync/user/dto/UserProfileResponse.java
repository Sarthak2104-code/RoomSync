package com.roomsync.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Authoritative response payload representing the authenticated user's profile.
 * Contains safe, non-sensitive profile attributes and assigned operational location details.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileResponse {

    private Long id;
    private String wissenId;
    private String name;
    private String email;
    private String role;
    private LocationSummary location;
    private boolean active;
    private boolean bookingEnabled;
    private OffsetDateTime createdAt;

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
}
