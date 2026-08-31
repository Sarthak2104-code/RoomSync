package com.roomsync.user.service;

import com.roomsync.location.entity.Location;
import com.roomsync.user.dto.UserProfileResponse;
import com.roomsync.user.entity.User;
import com.roomsync.user.exception.UserNotFoundException;
import com.roomsync.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service providing user domain operations and authoritative profile resolution.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    /**
     * Retrieves the authoritative profile for the specified user ID.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(Long userId) {
        if (userId == null) {
            throw new UserNotFoundException("Invalid user ID");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        UserProfileResponse.LocationSummary locationSummary = null;
        Location location = user.getLocation();
        if (location != null) {
            locationSummary = UserProfileResponse.LocationSummary.builder()
                    .id(location.getId())
                    .name(location.getName())
                    .code(location.getCode())
                    .timezone(location.getTimezone())
                    .build();
        }

        String roleName = user.getRole() != null ? user.getRole().getName() : "USER";

        return UserProfileResponse.builder()
                .id(user.getId())
                .wissenId(user.getWissenId())
                .name(user.getName())
                .email(user.getEmail())
                .role(roleName)
                .location(locationSummary)
                .active(user.isActive())
                .bookingEnabled(user.isBookingEnabled())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
