package com.roomsync.room.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;

public class AmenityNotFoundException extends RoomSyncException {
    public AmenityNotFoundException(Long id) {
        super(ErrorCode.BAD_REQUEST, String.format("Amenity with id '%d' was not found", id));
    }
}
