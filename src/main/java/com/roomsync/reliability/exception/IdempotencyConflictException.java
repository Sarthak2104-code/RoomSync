package com.roomsync.reliability.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;
import org.springframework.http.HttpStatus;

public class IdempotencyConflictException extends RoomSyncException {
    public IdempotencyConflictException(String message) {
        super(ErrorCode.IDEMPOTENCY_CONFLICT, HttpStatus.CONFLICT, message);
    }
}
