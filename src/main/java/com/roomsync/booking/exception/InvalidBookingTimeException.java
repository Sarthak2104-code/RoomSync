package com.roomsync.booking.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;

public class InvalidBookingTimeException extends RoomSyncException {
    public InvalidBookingTimeException(String message) {
        super(ErrorCode.INVALID_TIME, message);
    }
}
