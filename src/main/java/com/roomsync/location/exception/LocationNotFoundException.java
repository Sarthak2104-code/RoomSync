package com.roomsync.location.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;

public class LocationNotFoundException extends RoomSyncException {
    public LocationNotFoundException(Long id) {
        super(ErrorCode.LOCATION_NOT_FOUND, String.format("Location with id '%d' was not found", id));
    }

    public LocationNotFoundException(String message) {
        super(ErrorCode.LOCATION_NOT_FOUND, message);
    }
}
