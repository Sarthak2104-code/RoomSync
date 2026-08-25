package com.roomsync.room.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;

public class RoomAlreadyActiveException extends RoomSyncException {
    public RoomAlreadyActiveException(Long id) {
        super(ErrorCode.DUPLICATE_RESOURCE, String.format("Room with id '%d' is already active", id));
    }
}
