package com.roomsync.agent.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;
import org.springframework.http.HttpStatus;

public class OperationNotFoundException extends RoomSyncException {
    public OperationNotFoundException(String operationId) {
        super(ErrorCode.BAD_REQUEST, HttpStatus.NOT_FOUND, String.format("Operation '%s' not found", operationId));
    }
}
