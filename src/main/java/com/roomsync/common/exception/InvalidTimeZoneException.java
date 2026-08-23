package com.roomsync.common.exception;

/**
 * Thrown when an invalid or unparseable IANA timezone identifier is provided.
 */
public class InvalidTimeZoneException extends RoomSyncException {

    public InvalidTimeZoneException(String timeZone) {
        super(ErrorCode.INVALID_TIMEZONE, String.format("Invalid IANA timezone identifier: '%s'", timeZone));
    }

    public InvalidTimeZoneException(String message, Throwable cause) {
        super(ErrorCode.INVALID_TIMEZONE, message, cause);
    }
}
