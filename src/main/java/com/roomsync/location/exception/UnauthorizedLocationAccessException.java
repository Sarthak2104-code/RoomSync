package com.roomsync.location.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;

public class UnauthorizedLocationAccessException extends RoomSyncException {
    public UnauthorizedLocationAccessException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
