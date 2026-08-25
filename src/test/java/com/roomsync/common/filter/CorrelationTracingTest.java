package com.roomsync.common.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorrelationTracingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("H. Correlation tracing: preserves incoming X-Correlation-Id and returns it in response")
    void testPreserveCorrelationId() throws Exception {
        String testCorrId = "custom-trace-id-12345";

        mockMvc.perform(get("/v3/api-docs")
                        .header("X-Correlation-Id", testCorrId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", testCorrId));
    }

    @Test
    @DisplayName("H. Correlation tracing: generates X-Correlation-Id if none is provided")
    void testGenerateCorrelationId() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"));
    }
}
