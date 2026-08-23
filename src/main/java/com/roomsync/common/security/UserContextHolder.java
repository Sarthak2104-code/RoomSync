package com.roomsync.common.security;

import java.util.Optional;

/**
 * ThreadLocal context accessor for the currently authenticated user principal.
 * Enforces strict clear() lifecycle management.
 */
public final class UserContextHolder {

    private static final ThreadLocal<AuthenticatedUser> CURRENT_USER = new ThreadLocal<>();

    private UserContextHolder() {
        // Prevent instantiation
    }

    public static void set(AuthenticatedUser user) {
        CURRENT_USER.set(user);
    }

    public static Optional<AuthenticatedUser> get() {
        return Optional.ofNullable(CURRENT_USER.get());
    }

    public static Long getUserId() {
        return get().map(AuthenticatedUser::id).orElse(null);
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
