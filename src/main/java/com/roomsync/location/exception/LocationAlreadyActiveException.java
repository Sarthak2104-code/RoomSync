package com.roomsync.location.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;

public class LocationAlreadyActiveException extends RoomSyncException {
    public LocationAlreadyActiveException(Long id) {
        super(ErrorCode.DUPLICATE_RESOURCE, String.format("Location with id '%d' is already active", id));
    }
}
