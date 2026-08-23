package com.roomsync.auth.service;

import com.roomsync.auth.dto.AuthResponse;
import com.roomsync.auth.dto.LoginRequest;
import com.roomsync.auth.dto.RefreshTokenRequest;
import com.roomsync.auth.dto.TokenRefreshResponse;
import com.roomsync.security.exception.UnauthorizedException;
import com.roomsync.security.jwt.JwtTokenProvider;
import com.roomsync.user.entity.User;
import com.roomsync.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!user.isActive()) {
            log.warn("Login attempt for inactive user: {}", normalizedEmail);
            throw new UnauthorizedException("User account is inactive");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Invalid password for user: {}", normalizedEmail);
            throw new UnauthorizedException("Invalid email or password");
        }

        Long locationId = user.getLocation() != null ? user.getLocation().getId() : null;
        String role = user.getRole() != null ? user.getRole().getName() : "USER";

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), role, locationId);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getEmail());

        log.info("User successfully authenticated: id={}, email={}, role={}", user.getId(), user.getEmail(), role);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationMs() / 1000)
                .userId(user.getId())
                .email(user.getEmail())
                .role(role)
                .locationId(locationId)
                .build();
    }

    @Transactional(readOnly = true)
    public TokenRefreshResponse refreshToken(RefreshTokenRequest request) {
        String token = request.getRefreshToken();

        if (!jwtTokenProvider.validateToken(token) || !jwtTokenProvider.isRefreshToken(token)) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        if (userId == null) {
            throw new UnauthorizedException("Invalid refresh token payload");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (!user.isActive()) {
            throw new UnauthorizedException("User account is inactive");
        }

        Long locationId = user.getLocation() != null ? user.getLocation().getId() : null;
        String role = user.getRole() != null ? user.getRole().getName() : "USER";

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), role, locationId);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getEmail());

        return TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationMs() / 1000)
                .build();
    }
}
