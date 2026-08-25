package com.roomsync.agent.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;
import org.springframework.http.HttpStatus;

public class ForbiddenOperationAccessException extends RoomSyncException {
    public ForbiddenOperationAccessException(String message) {
        super(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, message);
    }
}
