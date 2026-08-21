package com.roomsync.room.exception;

public class DuplicateRoomNameException extends RuntimeException {
    public DuplicateRoomNameException(String name) {
        super(String.format("A room with the name '%s' already exists", name));
    }

    public DuplicateRoomNameException(String name, String locationName) {
        super(String.format("A room with the name '%s' already exists in location '%s'", name, locationName));
    }
}
