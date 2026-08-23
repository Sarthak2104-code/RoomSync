package com.roomsync.common.validation;

import com.roomsync.common.time.TimeUtils;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates that an incoming String value represents a valid IANA timezone.
 */
public class TimeZoneValidator implements ConstraintValidator<ValidTimeZone, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) {
            return true; // Use @NotNull or @NotBlank to enforce presence
        }
        return TimeUtils.isValidZoneId(value.trim());
    }
}
