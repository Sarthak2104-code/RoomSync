package com.roomsync.common.filter;

import com.roomsync.common.constants.CommonConstants;
import org.slf4j.MDC;

/**
 * Single authoritative context for the current request's correlation ID.
 * Synchronized with SLF4J MDC.
 */
public final class CorrelationContext {

    private static final ThreadLocal<String> CURRENT_CORRELATION_ID = new ThreadLocal<>();

    private CorrelationContext() {
        // Prevent instantiation
    }

    public static void set(String correlationId) {
        CURRENT_CORRELATION_ID.set(correlationId);
        if (correlationId != null) {
            MDC.put(CommonConstants.MDC_CORRELATION_ID_KEY, correlationId);
        } else {
            MDC.remove(CommonConstants.MDC_CORRELATION_ID_KEY);
        }
    }

    public static String get() {
        return CURRENT_CORRELATION_ID.get();
    }

    public static void clear() {
        CURRENT_CORRELATION_ID.remove();
        MDC.remove(CommonConstants.MDC_CORRELATION_ID_KEY);
    }
}
