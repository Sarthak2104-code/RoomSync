package com.roomsync.common.filter;

import com.roomsync.common.constants.CommonConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void tearDown() {
        CorrelationContext.clear();
    }

    @Test
    @DisplayName("Should preserve incoming X-Correlation-Id header and set on response and MDC")
    void shouldPreserveIncomingCorrelationId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CommonConstants.CORRELATION_ID_HEADER, "custom-corr-999");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(CorrelationContext.get()).isEqualTo("custom-corr-999");
            assertThat(MDC.get(CommonConstants.MDC_CORRELATION_ID_KEY)).isEqualTo("custom-corr-999");
        });

        assertThat(response.getHeader(CommonConstants.CORRELATION_ID_HEADER)).isEqualTo("custom-corr-999");
        // Verify ThreadLocal and MDC are cleaned up after request
        assertThat(CorrelationContext.get()).isNull();
        assertThat(MDC.get(CommonConstants.MDC_CORRELATION_ID_KEY)).isNull();
    }

    @Test
    @DisplayName("Should accept X-Request-Id as fallback when X-Correlation-Id is absent")
    void shouldAcceptRequestIdFallback() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CommonConstants.REQUEST_ID_HEADER, "fallback-req-888");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(CorrelationContext.get()).isEqualTo("fallback-req-888");
        });

        assertThat(response.getHeader(CommonConstants.CORRELATION_ID_HEADER)).isEqualTo("fallback-req-888");
        assertThat(CorrelationContext.get()).isNull();
    }

    @Test
    @DisplayName("Should generate UUID when no correlation header is provided")
    void shouldGenerateUuidWhenHeaderMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(CorrelationContext.get()).isNotNull().isNotBlank();
        });

        String generatedId = response.getHeader(CommonConstants.CORRELATION_ID_HEADER);
        assertThat(generatedId).isNotNull().isNotBlank();
        assertThat(CorrelationContext.get()).isNull();
    }
}
