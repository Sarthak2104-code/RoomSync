package com.roomsync.booking.exception;

public class BookingNotFoundException extends RuntimeException {
    public BookingNotFoundException(Long id) {
        super(String.format("Booking with id '%d' was not found", id));
    }

    public BookingNotFoundException(String message) {
        super(message);
    }
}
