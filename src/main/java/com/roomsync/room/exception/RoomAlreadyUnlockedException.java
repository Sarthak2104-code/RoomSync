package com.roomsync.room.exception;

public class RoomAlreadyUnlockedException extends RuntimeException {
    public RoomAlreadyUnlockedException(Long id) {
        super(String.format("Room with id '%d' is already AVAILABLE", id));
    }
}
