package com.roomsync.room.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;

public class RoomNotFoundException extends RoomSyncException {
    public RoomNotFoundException(Long id) {
        super(ErrorCode.ROOM_NOT_FOUND, String.format("Room with id '%d' was not found", id));
    }

    public RoomNotFoundException(String message) {
        super(ErrorCode.ROOM_NOT_FOUND, message);
    }
}
