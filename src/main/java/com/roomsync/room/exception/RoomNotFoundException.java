package com.roomsync.room.exception;

public class RoomNotFoundException extends RuntimeException {
    public RoomNotFoundException(Long id) {
        super(String.format("Room with id '%d' was not found or is inactive", id));
    }

    public RoomNotFoundException(String message) {
        super(message);
    }
}
