package com.roomsync.reliability.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;
import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends RoomSyncException {
    public RateLimitExceededException(String message) {
        super(ErrorCode.RATE_LIMIT_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
