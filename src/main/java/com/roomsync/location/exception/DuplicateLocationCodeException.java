package com.roomsync.location.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;

public class DuplicateLocationCodeException extends RoomSyncException {
    public DuplicateLocationCodeException(String code) {
        super(ErrorCode.DUPLICATE_RESOURCE, String.format("Location with code '%s' already exists", code));
    }
}
