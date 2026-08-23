package com.roomsync.common.constants;

public final class CommonConstants {

    private CommonConstants() {
        // Prevent instantiation
    }

    /**
     * Primary & canonical HTTP header for request correlation tracking.
     */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    /**
     * Incoming compatibility alias for correlation tracking.
     */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /**
     * SLF4J MDC key for correlation ID.
     */
    public static final String MDC_CORRELATION_ID_KEY = "correlationId";

    /**
     * Default system timezone.
     */
    public static final String DEFAULT_SYSTEM_TIMEZONE = "UTC";

    /**
     * Canonical Indian timezone.
     */
    public static final String DEFAULT_LOCATION_TIMEZONE = "Asia/Kolkata";

    /**
     * Legacy request header for user identification (for existing endpoint compatibility).
     */
    public static final String USER_ID_HEADER = "X-User-Id";

    /**
     * Standard HTTP Authorization header.
     */
    public static final String AUTHORIZATION_HEADER = "Authorization";
}
