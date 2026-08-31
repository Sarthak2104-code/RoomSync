package com.roomsync.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        properties.setAccessTokenExpirationMs(3600000);
        properties.setRefreshTokenExpirationMs(604800000);

        jwtTokenProvider = new JwtTokenProvider(properties);
        jwtTokenProvider.init();
    }

    @Test
    @DisplayName("Should generate and validate valid access token with all claims")
    void testGenerateAndValidateAccessToken() {
        String token = jwtTokenProvider.generateAccessToken(10L, "WT5128", "user@roomsync.com", "USER", 1L);

        assertThat(token).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.isRefreshToken(token)).isFalse();
        assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(10L);
        assertThat(jwtTokenProvider.getWissenIdFromToken(token)).isEqualTo("WT5128");
        assertThat(jwtTokenProvider.getEmailFromToken(token)).isEqualTo("user@roomsync.com");
        assertThat(jwtTokenProvider.getRoleFromToken(token)).isEqualTo("USER");
        assertThat(jwtTokenProvider.getLocationIdFromToken(token)).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should generate and validate valid refresh token")
    void testGenerateAndValidateRefreshToken() {
        String token = jwtTokenProvider.generateRefreshToken(10L, "WT5128", "user@roomsync.com");

        assertThat(token).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.isRefreshToken(token)).isTrue();
        assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(10L);
        assertThat(jwtTokenProvider.getWissenIdFromToken(token)).isEqualTo("WT5128");
        assertThat(jwtTokenProvider.getEmailFromToken(token)).isEqualTo("user@roomsync.com");
    }

    @Test
    @DisplayName("Should reject invalid or malformed tokens")
    void testRejectInvalidToken() {
        assertThat(jwtTokenProvider.validateToken("invalid.token.string")).isFalse();
        assertThat(jwtTokenProvider.validateToken(null)).isFalse();
        assertThat(jwtTokenProvider.validateToken("")).isFalse();
    }
}
