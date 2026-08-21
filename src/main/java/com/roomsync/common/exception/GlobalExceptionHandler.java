package com.roomsync.common.exception;

import com.roomsync.booking.exception.BookingAlreadyCancelledException;
import com.roomsync.booking.exception.BookingAlreadyCompletedException;
import com.roomsync.booking.exception.BookingNotFoundException;
import com.roomsync.booking.exception.BookingOverlapException;
import com.roomsync.booking.exception.InvalidBookingTimeException;
import com.roomsync.booking.exception.RoomNotBookableException;
import com.roomsync.booking.exception.UnauthorizedBookingOperationException;
import com.roomsync.common.response.ErrorResponse;
import com.roomsync.location.exception.DuplicateLocationCodeException;
import com.roomsync.location.exception.DuplicateLocationNameException;
import com.roomsync.location.exception.LocationAlreadyInactiveException;
import com.roomsync.location.exception.LocationNotActiveException;
import com.roomsync.location.exception.LocationNotFoundException;
import com.roomsync.location.exception.UnauthorizedLocationAccessException;
import com.roomsync.room.exception.DuplicateRoomNameException;
import com.roomsync.room.exception.RoomAlreadyInactiveException;
import com.roomsync.room.exception.RoomAlreadyLockedException;
import com.roomsync.room.exception.RoomAlreadyUnlockedException;
import com.roomsync.room.exception.RoomNotFoundException;
import com.roomsync.user.exception.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({
            RoomNotFoundException.class,
            UserNotFoundException.class,
            BookingNotFoundException.class,
            LocationNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFoundExceptions(RuntimeException ex, HttpServletRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error(HttpStatus.NOT_FOUND.name())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler({
            UnauthorizedBookingOperationException.class,
            UnauthorizedLocationAccessException.class
    })
    public ResponseEntity<ErrorResponse> handleUnauthorizedOperation(RuntimeException ex, HttpServletRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error(HttpStatus.FORBIDDEN.name())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler({
            DuplicateRoomNameException.class,
            RoomAlreadyLockedException.class,
            RoomAlreadyUnlockedException.class,
            RoomAlreadyInactiveException.class,
            RoomNotBookableException.class,
            BookingOverlapException.class,
            BookingAlreadyCancelledException.class,
            BookingAlreadyCompletedException.class,
            DuplicateLocationCodeException.class,
            DuplicateLocationNameException.class,
            LocationAlreadyInactiveException.class,
            LocationNotActiveException.class
    })
    public ResponseEntity<ErrorResponse> handleConflictExceptions(RuntimeException ex, HttpServletRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error(HttpStatus.CONFLICT.name())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * Handles database constraint violations specifically inspecting PostgreSQL ServerErrorMessage.
     * Maps 'no_overlapping_bookings' exclusion constraint to 409 CONFLICT without string pattern matching.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.error("Data integrity violation while processing request: {}", request.getRequestURI(), ex);

        PSQLException psqlException = extractPSQLException(ex);
        if (psqlException != null && psqlException.getServerErrorMessage() != null) {
            String constraint = psqlException.getServerErrorMessage().getConstraint();
            if ("no_overlapping_bookings".equals(constraint)) {
                ErrorResponse response = ErrorResponse.builder()
                        .timestamp(OffsetDateTime.now())
                        .status(HttpStatus.CONFLICT.value())
                        .error(HttpStatus.CONFLICT.name())
                        .message("The room is already booked for the selected time.")
                        .path(request.getRequestURI())
                        .build();
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
        }

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error(HttpStatus.CONFLICT.name())
                .message("Data integrity conflict occurred")
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(InvalidBookingTimeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBookingTime(InvalidBookingTimeException ex, HttpServletRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.name())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        String message = ex.getHeaderName().equalsIgnoreCase("X-User-Id")
                ? "X-User-Id header is required"
                : ex.getMessage();

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.name())
                .message(message)
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = ex.getName().equalsIgnoreCase("userId") || ex.getName().equalsIgnoreCase("X-User-Id")
                ? "Invalid X-User-Id header value"
                : String.format("Invalid parameter value for '%s'", ex.getName());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.name())
                .message(message)
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.name())
                .message("Validation failed for one or more fields")
                .path(request.getRequestURI())
                .validationErrors(errors)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.name())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error while processing request: {}", request.getRequestURI(), ex);

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.name())
                .message("An unexpected error occurred")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

    private PSQLException extractPSQLException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof PSQLException psqlException) {
                return psqlException;
            }
            current = current.getCause();
        }
        return null;
    }
}
