package com.roomsync.booking.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;

public class RoomNotBookableException extends RoomSyncException {
    public RoomNotBookableException(Long roomId, String reason) {
        super(ErrorCode.ROOM_LOCKED, String.format("Room with id '%d' is not bookable: %s", roomId, reason));
    }

    public RoomNotBookableException(String message) {
        super(ErrorCode.ROOM_LOCKED, message);
    }
}
