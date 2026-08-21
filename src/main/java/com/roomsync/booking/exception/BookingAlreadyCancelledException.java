package com.roomsync.booking.exception;

public class BookingAlreadyCancelledException extends RuntimeException {
    public BookingAlreadyCancelledException(Long id) {
        super(String.format("Booking with id '%d' is already CANCELLED", id));
    }
}
