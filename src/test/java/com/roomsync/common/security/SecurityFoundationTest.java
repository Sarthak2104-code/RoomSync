package com.roomsync.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityFoundationTest {

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    @DisplayName("AuthenticatedUser: Should represent principal correctly")
    void testAuthenticatedUser() {
        AuthenticatedUser user = new AuthenticatedUser(10L, "admin@roomsync.com", SecurityConstants.ROLE_ADMIN, 1L);

        assertThat(user.id()).isEqualTo(10L);
        assertThat(user.email()).isEqualTo("admin@roomsync.com");
        assertThat(user.role()).isEqualTo("ADMIN");
        assertThat(user.locationId()).isEqualTo(1L);
        assertThat(user.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("UserContextHolder: Should manage ThreadLocal lifecycle properly")
    void testUserContextHolderLifecycle() {
        assertThat(UserContextHolder.get()).isEmpty();
        assertThat(UserContextHolder.getUserId()).isNull();

        AuthenticatedUser user = new AuthenticatedUser(5L, "user@roomsync.com", SecurityConstants.ROLE_USER, 2L);
        UserContextHolder.set(user);

        assertThat(UserContextHolder.get()).isPresent().contains(user);
        assertThat(UserContextHolder.getUserId()).isEqualTo(5L);

        UserContextHolder.clear();
        assertThat(UserContextHolder.get()).isEmpty();
        assertThat(UserContextHolder.getUserId()).isNull();
    }

    @Test
    @DisplayName("SecurityConstants: Should map database roles to Spring Security authorities consistently")
    void testRoleAuthorityMapping() {
        assertThat(SecurityConstants.toAuthority("USER")).isEqualTo("ROLE_USER");
        assertThat(SecurityConstants.toAuthority("ADMIN")).isEqualTo("ROLE_ADMIN");
        assertThat(SecurityConstants.toAuthority("ROLE_USER")).isEqualTo("ROLE_USER");
        assertThat(SecurityConstants.toAuthority(null)).isNull();
    }
}
