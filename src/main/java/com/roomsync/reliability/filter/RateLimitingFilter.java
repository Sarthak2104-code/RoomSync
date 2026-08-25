package com.roomsync.reliability.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.filter.CorrelationContext;
import com.roomsync.common.response.ErrorResponse;
import com.roomsync.common.security.AuthenticatedUser;
import com.roomsync.common.security.UserContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory Token Bucket Rate Limiting Filter per authenticated user.
 * NOTE: IN-MEMORY RATE LIMITING = SINGLE INSTANCE ONLY.
 */
@Component
@Order(3) // After CorrelationIdFilter (1) and JwtAuthenticationFilter (2)
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final int capacity;
    private final boolean enabled;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitingFilter(
            ObjectMapper objectMapper,
            @Value("${roomsync.rate-limit.capacity:1000}") int capacity,
            @Value("${roomsync.rate-limit.enabled:true}") boolean enabled) {
        this.objectMapper = objectMapper;
        this.capacity = capacity;
        this.enabled = enabled;
    }

    public void clearBuckets() {
        buckets.clear();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = resolveRateLimitKey(request);

        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(capacity, 60_000L));

        if (!bucket.tryConsume()) {
            log.warn("Rate limit exceeded for client key: {} on URI: {}", key, request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", "60");

            ErrorResponse errorResponse = ErrorResponse.builder()
                    .timestamp(OffsetDateTime.now())
                    .status(HttpStatus.TOO_MANY_REQUESTS.value())
                    .error(HttpStatus.TOO_MANY_REQUESTS.name())
                    .errorCode(ErrorCode.RATE_LIMIT_EXCEEDED.name())
                    .message("Rate limit exceeded. Please try again later.")
                    .path(request.getRequestURI())
                    .correlationId(CorrelationContext.get())
                    .build();

            objectMapper.writeValue(response.getOutputStream(), errorResponse);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveRateLimitKey(HttpServletRequest request) {
        if (UserContextHolder.get().isPresent()) {
            return "user_" + UserContextHolder.get().get().id();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return "user_" + user.id();
        }
        String ip = request.getRemoteAddr();
        return "ip_" + (ip != null ? ip : "unknown");
    }

    private static class TokenBucket {
        private final int maxTokens;
        private final long refillIntervalMs;
        private final AtomicInteger tokens;
        private final AtomicLong lastRefillTime;

        public TokenBucket(int maxTokens, long refillIntervalMs) {
            this.maxTokens = maxTokens;
            this.refillIntervalMs = refillIntervalMs;
            this.tokens = new AtomicInteger(maxTokens);
            this.lastRefillTime = new AtomicLong(System.currentTimeMillis());
        }

        public synchronized boolean tryConsume() {
            refill();
            if (tokens.get() > 0) {
                tokens.decrementAndGet();
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTime.get();
            if (elapsed > refillIntervalMs) {
                tokens.set(maxTokens);
                lastRefillTime.set(now);
            }
        }
    }
}
