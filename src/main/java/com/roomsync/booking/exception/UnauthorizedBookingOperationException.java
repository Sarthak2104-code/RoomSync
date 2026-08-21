package com.roomsync.booking.exception;

public class UnauthorizedBookingOperationException extends RuntimeException {
    public UnauthorizedBookingOperationException(String message) {
        super(message);
    }
}
