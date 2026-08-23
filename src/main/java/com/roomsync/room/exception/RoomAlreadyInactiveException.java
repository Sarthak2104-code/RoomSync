package com.roomsync.room.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;

public class RoomAlreadyInactiveException extends RoomSyncException {
    public RoomAlreadyInactiveException(Long id) {
        super(ErrorCode.ROOM_INACTIVE, String.format("Room with id '%d' is already inactive", id));
    }

    public RoomAlreadyInactiveException(String message) {
        super(ErrorCode.ROOM_INACTIVE, message);
    }
}
