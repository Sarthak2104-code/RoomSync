package com.roomsync.reliability.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;
import org.springframework.http.HttpStatus;

public class IdempotencyKeyRequiredException extends RoomSyncException {
    public IdempotencyKeyRequiredException(String message) {
        super(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, HttpStatus.BAD_REQUEST, message);
    }
}
