package com.roomsync.room.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;

public class RoomAlreadyUnlockedException extends RoomSyncException {
    public RoomAlreadyUnlockedException(Long id) {
        super(ErrorCode.ROOM_ALREADY_UNLOCKED, String.format("Room with id '%d' is already UNLOCKED", id));
    }

    public RoomAlreadyUnlockedException(String message) {
        super(ErrorCode.ROOM_ALREADY_UNLOCKED, message);
    }
}
