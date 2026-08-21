package com.roomsync.location.exception;

public class LocationNotFoundException extends RuntimeException {
    public LocationNotFoundException(Long id) {
        super(String.format("Location with id '%d' was not found", id));
    }

    public LocationNotFoundException(String message) {
        super(message);
    }
}
