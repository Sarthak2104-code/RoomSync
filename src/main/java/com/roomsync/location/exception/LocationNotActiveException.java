package com.roomsync.location.exception;

public class LocationNotActiveException extends RuntimeException {
    public LocationNotActiveException(String message) {
        super(message);
    }
}
