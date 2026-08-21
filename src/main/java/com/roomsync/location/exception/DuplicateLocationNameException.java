package com.roomsync.location.exception;

public class DuplicateLocationNameException extends RuntimeException {
    public DuplicateLocationNameException(String name) {
        super(String.format("A location with the name '%s' already exists", name));
    }
}
