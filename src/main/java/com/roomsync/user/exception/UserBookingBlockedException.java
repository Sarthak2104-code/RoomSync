package com.roomsync.user.exception;

import com.roomsync.common.exception.ErrorCode;
import com.roomsync.common.exception.RoomSyncException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a user with restricted booking access attempts to create,
 * reschedule, or allocate room bookings.
 */
public class UserBookingBlockedException extends RoomSyncException {

    public UserBookingBlockedException(String message) {
        super(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, message);
    }

    public UserBookingBlockedException() {
        super(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Your account is currently restricted from creating or modifying room bookings. Please contact an administrator.");
    }
}
