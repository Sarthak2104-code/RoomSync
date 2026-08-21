package com.roomsync.user.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long id) {
        super(String.format("User with id '%d' was not found", id));
    }

    public UserNotFoundException(String message) {
        super(message);
    }
}
