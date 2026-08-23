package com.roomsync.booking.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;

public class BookingNotFoundException extends RoomSyncException {
    public BookingNotFoundException(Long id) {
        super(ErrorCode.BOOKING_NOT_FOUND, String.format("Booking with id '%d' was not found", id));
    }

    public BookingNotFoundException(String message) {
        super(ErrorCode.BOOKING_NOT_FOUND, message);
    }
}
