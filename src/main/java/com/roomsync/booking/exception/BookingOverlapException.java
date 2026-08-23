package com.roomsync.booking.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;

public class BookingOverlapException extends RoomSyncException {
    public BookingOverlapException() {
        super(ErrorCode.BOOKING_CONFLICT, "The requested time slot overlaps with an existing confirmed booking");
    }

    public BookingOverlapException(String message) {
        super(ErrorCode.BOOKING_CONFLICT, message);
    }
}
