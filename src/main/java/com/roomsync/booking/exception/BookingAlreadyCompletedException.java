package com.roomsync.booking.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;

public class BookingAlreadyCompletedException extends RoomSyncException {
    public BookingAlreadyCompletedException(Long id) {
        super(ErrorCode.BOOKING_COMPLETED, String.format("Booking with id '%d' is already COMPLETED", id));
    }

    public BookingAlreadyCompletedException(String message) {
        super(ErrorCode.BOOKING_COMPLETED, message);
    }
}
