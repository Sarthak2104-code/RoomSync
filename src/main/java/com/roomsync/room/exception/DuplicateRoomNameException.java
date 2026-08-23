package com.roomsync.room.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;

public class DuplicateRoomNameException extends RoomSyncException {
    public DuplicateRoomNameException(String name, String locationName) {
        super(ErrorCode.DUPLICATE_RESOURCE, String.format("A room with the name '%s' already exists in location '%s'", name, locationName));
    }

    public DuplicateRoomNameException(String name, Long locationId) {
        super(ErrorCode.DUPLICATE_RESOURCE, String.format("A room with the name '%s' already exists in location '%d'", name, locationId));
    }

    public DuplicateRoomNameException(String message) {
        super(ErrorCode.DUPLICATE_RESOURCE, message);
    }
}
