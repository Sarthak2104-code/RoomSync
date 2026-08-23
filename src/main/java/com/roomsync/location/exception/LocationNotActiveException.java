package com.roomsync.location.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;

public class LocationNotActiveException extends RoomSyncException {
    public LocationNotActiveException(Long id) {
        super(ErrorCode.LOCATION_INACTIVE, String.format("Location with id '%d' is not active", id));
    }

    public LocationNotActiveException(String message) {
        super(ErrorCode.LOCATION_INACTIVE, message);
    }
}
