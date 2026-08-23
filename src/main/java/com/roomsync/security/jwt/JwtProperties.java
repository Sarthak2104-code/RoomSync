package com.roomsync.security.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Type-safe configuration properties for JWT authentication.
 */
@Component
@ConfigurationProperties(prefix = "roomsync.security.jwt")
@Getter
@Setter
public class JwtProperties {

    /**
     * Secret key for signing HMAC-SHA JWTs (minimum 256 bits).
     */
    private String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    /**
     * Access token validity in milliseconds (default: 1 hour).
     */
    private long accessTokenExpirationMs = 3600000;

    /**
     * Refresh token validity in milliseconds (default: 7 days).
     */
    private long refreshTokenExpirationMs = 604800000;
}
