package com.roomsync.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class WissenIdValidationTest {

    private static final String WISSEN_ID_REGEX = "^(WT|WI)[0-9]+$";
    private static final Pattern PATTERN = Pattern.compile(WISSEN_ID_REGEX);

    @ParameterizedTest(name = "Valid format: {0}")
    @ValueSource(strings = {"WT5128", "WI422", "WT1", "WI999999", "WT0001", "WI123456789"})
    @DisplayName("Should accept valid Wissen ID formats matching ^(WT|WI)[0-9]+$")
    void testValidWissenIdFormats(String input) {
        assertThat(PATTERN.matcher(input).matches()).isTrue();
    }

    @ParameterizedTest(name = "Invalid format: {0}")
    @ValueSource(strings = {"WT", "WI", "WX422", "12345", "WTABC", "WIABC", "WT-422", "WT 422", "wt5128", "wi422", "", "   "})
    @DisplayName("Should reject invalid Wissen ID formats")
    void testInvalidWissenIdFormats(String input) {
        assertThat(PATTERN.matcher(input).matches()).isFalse();
    }

    @Test
    @DisplayName("Should normalize lowercase inputs through trim and uppercase transformation")
    void testNormalization() {
        String input1 = " wt5128 ";
        String normalized1 = input1.trim().toUpperCase();
        assertThat(normalized1).isEqualTo("WT5128");
        assertThat(PATTERN.matcher(normalized1).matches()).isTrue();

        String input2 = "wi422";
        String normalized2 = input2.trim().toUpperCase();
        assertThat(normalized2).isEqualTo("WI422");
        assertThat(PATTERN.matcher(normalized2).matches()).isTrue();
    }
}
