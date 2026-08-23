package com.roomsync.security;

import com.roomsync.common.security.AuthenticatedUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

public final class TestSecurityUtils {

    private TestSecurityUtils() {}

    public static RequestPostProcessor userAuth(Long userId, String role) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "user" + userId + "@roomsync.com", role, 1L);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        return request -> {
            SecurityContextHolder.getContext().setAuthentication(auth);
            return authentication(auth).postProcessRequest(request);
        };
    }

    public static RequestPostProcessor userAuth(Long userId) {
        return userAuth(userId, "USER");
    }

    public static RequestPostProcessor adminAuth(Long userId) {
        return userAuth(userId, "ADMIN");
    }
}
