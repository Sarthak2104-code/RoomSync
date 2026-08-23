package com.roomsync.user.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;

public class UserNotFoundException extends RoomSyncException {
    public UserNotFoundException(Long id) {
        super(ErrorCode.USER_NOT_FOUND, String.format("User with id '%d' was not found", id));
    }

    public UserNotFoundException(String message) {
        super(ErrorCode.USER_NOT_FOUND, message);
    }
}
