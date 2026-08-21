package com.roomsync.booking.exception;

public class BookingAlreadyCompletedException extends RuntimeException {
    public BookingAlreadyCompletedException(Long id) {
        super(String.format("Booking with id '%d' has already COMPLETED and cannot be modified or cancelled", id));
    }
}
