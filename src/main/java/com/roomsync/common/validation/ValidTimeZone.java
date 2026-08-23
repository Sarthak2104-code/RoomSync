package com.roomsync.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a string is a valid IANA timezone identifier (e.g. 'Asia/Kolkata', 'America/New_York').
 */
@Documented
@Constraint(validatedBy = TimeZoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTimeZone {

    String message() default "Must be a valid IANA timezone identifier (e.g. 'Asia/Kolkata', 'America/New_York', 'UTC')";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
