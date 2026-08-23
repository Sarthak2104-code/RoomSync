package com.roomsync.booking.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;

public class BookingAlreadyCancelledException extends RoomSyncException {
    public BookingAlreadyCancelledException(Long id) {
        super(ErrorCode.BOOKING_CONFLICT, String.format("Booking with id '%d' is already CANCELLED", id));
    }

    public BookingAlreadyCancelledException(String message) {
        super(ErrorCode.BOOKING_CONFLICT, message);
    }
}
