package com.roomsync.admin.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;

public class AdminRequestNotFoundException extends RoomSyncException {
    public AdminRequestNotFoundException(Long id) {
        super(ErrorCode.BAD_REQUEST, String.format("AdminRequest with id '%d' was not found", id));
    }
}
