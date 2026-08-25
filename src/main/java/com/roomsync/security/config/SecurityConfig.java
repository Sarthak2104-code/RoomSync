package com.roomsync.security.config;

import com.roomsync.common.filter.CorrelationIdFilter;
import com.roomsync.security.entrypoint.CustomAuthenticationEntryPoint;
import com.roomsync.security.handler.CustomAccessDeniedHandler;
import com.roomsync.security.jwt.JwtAuthenticationFilter;
import com.roomsync.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration establishing stateless JWT authentication and role-based authorization.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final CorrelationIdFilter correlationIdFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        // Public authentication endpoints
                        .requestMatchers("/api/auth/**").permitAll()

                        // Room, Location & Amenity read endpoints accessible by all authenticated users
                        .requestMatchers(HttpMethod.GET, "/api/rooms/**", "/api/locations/**", "/api/amenities/**").authenticated()

                        // Room, Location & Amenity mutation endpoints restricted to ADMIN
                        .requestMatchers(HttpMethod.POST, "/api/rooms/**", "/api/locations/**", "/api/amenities/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/rooms/**", "/api/locations/**", "/api/amenities/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/rooms/**", "/api/locations/**", "/api/amenities/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/rooms/**", "/api/locations/**", "/api/amenities/**").hasRole("ADMIN")

                        // Dedicated Admin endpoints
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Bookings accessible to authenticated users (ownership enforced in service/controller)
                        .requestMatchers("/api/bookings/**").authenticated()

                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
