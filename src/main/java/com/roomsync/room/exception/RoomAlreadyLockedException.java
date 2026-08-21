package com.roomsync.room.exception;

public class RoomAlreadyLockedException extends RuntimeException {
    public RoomAlreadyLockedException(Long id) {
        super(String.format("Room with id '%d' is already LOCKED", id));
    }
}
