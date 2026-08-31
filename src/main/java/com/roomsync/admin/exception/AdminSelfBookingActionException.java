package com.roomsync.admin.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;

public class AdminSelfBookingActionException extends RoomSyncException {

    public AdminSelfBookingActionException() {
        super(ErrorCode.BAD_REQUEST, "Administrators cannot modify their own booking access permissions.");
    }

    public AdminSelfBookingActionException(String message) {
        super(ErrorCode.BAD_REQUEST, message);
    }
}
