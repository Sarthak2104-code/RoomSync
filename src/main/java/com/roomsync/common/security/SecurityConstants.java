package com.roomsync.common.security;

public final class SecurityConstants {

    private SecurityConstants() {
        // Prevent instantiation
    }

    /**
     * Canonical database role values (stored in 'roles' table).
     */
    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    /**
     * Spring Security authority names.
     */
    public static final String AUTHORITY_USER = "ROLE_USER";
    public static final String AUTHORITY_ADMIN = "ROLE_ADMIN";

    /**
     * Request headers.
     */
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_AUTHORIZATION = "Authorization";

    /**
     * Maps a database canonical role name ('USER', 'ADMIN') to its corresponding Spring Security authority ('ROLE_USER', 'ROLE_ADMIN').
     */
    public static final String toAuthority(String role) {
        if (role == null) {
            return null;
        }
        String trimmed = role.trim().toUpperCase();
        if (trimmed.startsWith("ROLE_")) {
            return trimmed;
        }
        return "ROLE_" + trimmed;
    }
}
