package com.roomsync.reliability;

import com.roomsync.reliability.filter.RateLimitingFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Test
    @DisplayName("F. Rate limiting: requests under threshold succeed; exceeding threshold returns 429 with Retry-After")
    void testRateLimitingBehavior() throws Exception {
        rateLimitingFilter.clearBuckets();

        // Normal traffic under threshold succeeds
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());

        // Simulate consuming all bucket tokens
        for (int i = 0; i < 1100; i++) {
            mockMvc.perform(get("/v3/api-docs"));
        }

        // Exceeded threshold returns 429 + RATE_LIMIT_EXCEEDED + Retry-After
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));

        rateLimitingFilter.clearBuckets();
    }
}
