package com.roomsync.room.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;

public class DuplicateAmenityNameException extends RoomSyncException {
    public DuplicateAmenityNameException(String name) {
        super(ErrorCode.DUPLICATE_RESOURCE, String.format("Amenity with name '%s' already exists", name));
    }
}
