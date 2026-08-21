package com.roomsync.booking.exception;

public class RoomNotBookableException extends RuntimeException {
    public RoomNotBookableException(String message) {
        super(message);
    }
}
