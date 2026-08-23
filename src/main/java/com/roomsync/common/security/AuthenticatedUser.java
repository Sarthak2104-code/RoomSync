package com.roomsync.common.security;

import java.io.Serializable;

/**
 * Immutable representation of an authenticated user principal.
 */
public record AuthenticatedUser(
        Long id,
        String email,
        String role,
        Long locationId
) implements Serializable {

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role) || "ROLE_ADMIN".equalsIgnoreCase(role);
    }
}
