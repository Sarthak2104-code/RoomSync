package com.roomsync.location.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;

public class LocationAlreadyInactiveException extends RoomSyncException {
    public LocationAlreadyInactiveException(Long id) {
        super(ErrorCode.LOCATION_INACTIVE, String.format("Location with id '%d' is already inactive", id));
    }
}
