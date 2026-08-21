package com.roomsync.room.exception;

public class RoomAlreadyInactiveException extends RuntimeException {
    public RoomAlreadyInactiveException(Long id) {
        super(String.format("Room with id '%d' is already inactive", id));
    }
}
