package com.roomsync.location.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;

public class DuplicateLocationNameException extends RoomSyncException {
    public DuplicateLocationNameException(String name) {
        super(ErrorCode.DUPLICATE_RESOURCE, String.format("Location with name '%s' already exists", name));
    }
}
