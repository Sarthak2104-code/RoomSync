package com.roomsync.room.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;

public class RoomAlreadyLockedException extends RoomSyncException {
    public RoomAlreadyLockedException(Long id) {
        super(ErrorCode.ROOM_ALREADY_LOCKED, String.format("Room with id '%d' is already LOCKED", id));
    }

    public RoomAlreadyLockedException(String message) {
        super(ErrorCode.ROOM_ALREADY_LOCKED, message);
    }
}
