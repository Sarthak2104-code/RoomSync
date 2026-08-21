package com.roomsync.location.exception;

public class DuplicateLocationCodeException extends RuntimeException {
    public DuplicateLocationCodeException(String code) {
        super(String.format("A location with the code '%s' already exists", code));
    }
}
