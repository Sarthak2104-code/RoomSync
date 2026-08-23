package com.roomsync.common.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TimeZoneValidatorTest {

    private static Validator validator;

    private static class TestDto {
        @ValidTimeZone
        private final String timezone;

        public TestDto(String timezone) {
            this.timezone = timezone;
        }
    }

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Should accept valid IANA timezone identifiers")
    void testValidTimeZones() {
        assertThat(validator.validate(new TestDto("Asia/Kolkata"))).isEmpty();
        assertThat(validator.validate(new TestDto("America/New_York"))).isEmpty();
        assertThat(validator.validate(new TestDto("Europe/London"))).isEmpty();
        assertThat(validator.validate(new TestDto("UTC"))).isEmpty();
        assertThat(validator.validate(new TestDto(null))).isEmpty(); // null is valid for @ValidTimeZone, use @NotNull if required
    }

    @Test
    @DisplayName("Should reject invalid IANA timezone identifiers")
    void testInvalidTimeZones() {
        Set<ConstraintViolation<TestDto>> violations1 = validator.validate(new TestDto("Invalid/Timezone"));
        assertThat(violations1).hasSize(1);
        assertThat(violations1.iterator().next().getMessage()).contains("valid IANA timezone");

        Set<ConstraintViolation<TestDto>> violations2 = validator.validate(new TestDto("GMT+05:30"));
        assertThat(violations2).hasSize(1);
    }
}
