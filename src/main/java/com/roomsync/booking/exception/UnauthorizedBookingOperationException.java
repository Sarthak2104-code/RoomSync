package com.roomsync.booking.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;

public class UnauthorizedBookingOperationException extends RoomSyncException {
    public UnauthorizedBookingOperationException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
