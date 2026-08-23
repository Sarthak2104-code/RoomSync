package com.roomsync.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base abstract domain exception for RoomSync.
 * Carries a machine-readable ErrorCode, an HTTP status, and a human-readable message.
 */
@Getter
public abstract class RoomSyncException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;

    protected RoomSyncException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = errorCode.getDefaultHttpStatus();
    }

    protected RoomSyncException(ErrorCode errorCode, HttpStatus httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    protected RoomSyncException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = errorCode.getDefaultHttpStatus();
    }
}
