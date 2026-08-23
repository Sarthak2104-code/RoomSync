package com.roomsync.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Canonical RoomSync machine-readable error codes and their default HTTP status mappings.
 */
@Getter
public enum ErrorCode {

    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND),
    ROOM_LOCKED(HttpStatus.CONFLICT),
    LOCATION_INACTIVE(HttpStatus.CONFLICT),
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND),
    BOOKING_CONFLICT(HttpStatus.CONFLICT),
    BOOKING_COMPLETED(HttpStatus.CONFLICT),
    INVALID_TIME(HttpStatus.BAD_REQUEST),
    INVALID_TIMEZONE(HttpStatus.BAD_REQUEST),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    LOCATION_NOT_FOUND(HttpStatus.NOT_FOUND),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT),
    ROOM_INACTIVE(HttpStatus.CONFLICT),
    ROOM_ALREADY_LOCKED(HttpStatus.CONFLICT),
    ROOM_ALREADY_UNLOCKED(HttpStatus.CONFLICT),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus defaultHttpStatus;

    ErrorCode(HttpStatus defaultHttpStatus) {
        this.defaultHttpStatus = defaultHttpStatus;
    }
}
