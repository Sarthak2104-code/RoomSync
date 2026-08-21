package com.roomsync.location.exception;

public class UnauthorizedLocationAccessException extends RuntimeException {
    public UnauthorizedLocationAccessException(String message) {
        super(message);
    }
}
