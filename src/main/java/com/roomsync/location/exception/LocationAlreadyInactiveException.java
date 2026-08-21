package com.roomsync.location.exception;

public class LocationAlreadyInactiveException extends RuntimeException {
    public LocationAlreadyInactiveException(Long id) {
        super(String.format("Location with id '%d' is already inactive", id));
    }
}
