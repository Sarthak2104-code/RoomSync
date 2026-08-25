package com.roomsync.common.exception;

import com.roomsync.common.filter.CorrelationContext;
import com.roomsync.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized Global Exception Handler for all RoomSync REST endpoints.
 * Produces standardized ErrorResponse payloads with machine-readable error codes and correlation tracking.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(RoomSyncException.class)
    public ResponseEntity<ErrorResponse> handleRoomSyncException(RoomSyncException ex, HttpServletRequest request) {
        HttpStatus status = ex.getHttpStatus() != null ? ex.getHttpStatus() : HttpStatus.BAD_REQUEST;
        ErrorCode errorCode = ex.getErrorCode() != null ? ex.getErrorCode() : ErrorCode.BAD_REQUEST;

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(status.value())
                .error(status.name())
                .errorCode(errorCode.name())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .correlationId(CorrelationContext.get())
                .build();

        return ResponseEntity.status(status).body(response);
    }

    /**
     * Handles database constraint violations specifically inspecting PostgreSQL ServerErrorMessage.
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
                        .errorCode(ErrorCode.BOOKING_CONFLICT.name())
                        .message("The room is already booked for the selected time.")
                        .path(request.getRequestURI())
                        .correlationId(CorrelationContext.get())
                        .build();
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }

            if (constraint != null && (constraint.startsWith("ux_") || constraint.endsWith("_key") || constraint.contains("unique"))) {
                ErrorResponse response = ErrorResponse.builder()
                        .timestamp(OffsetDateTime.now())
                        .status(HttpStatus.CONFLICT.value())
                        .error(HttpStatus.CONFLICT.name())
                        .errorCode(ErrorCode.DUPLICATE_RESOURCE.name())
                        .message("A resource with the specified unique field already exists.")
                        .path(request.getRequestURI())
                        .correlationId(CorrelationContext.get())
                        .build();
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
        }

        // Deterministic fallback for unknown integrity violations
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.name())
                .errorCode(ErrorCode.BAD_REQUEST.name())
                .message("Data integrity constraint violation occurred")
                .path(request.getRequestURI())
                .correlationId(CorrelationContext.get())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        boolean isTimeZoneError = false;

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
            if ("timezone".equalsIgnoreCase(fieldError.getField()) || (fieldError.getDefaultMessage() != null && fieldError.getDefaultMessage().contains("timezone"))) {
                isTimeZoneError = true;
            }
        }

        String errorCode = isTimeZoneError ? ErrorCode.INVALID_TIMEZONE.name() : ErrorCode.VALIDATION_FAILED.name();

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.name())
                .errorCode(errorCode)
                .message("Validation failed for one or more fields")
                .path(request.getRequestURI())
                .correlationId(CorrelationContext.get())
                .validationErrors(errors)
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
                .errorCode(ErrorCode.BAD_REQUEST.name())
                .message(message)
                .path(request.getRequestURI())
                .correlationId(CorrelationContext.get())
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
                .errorCode(ErrorCode.BAD_REQUEST.name())
                .message(message)
                .path(request.getRequestURI())
                .correlationId(CorrelationContext.get())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.name())
                .errorCode(ErrorCode.BAD_REQUEST.name())
                .message(String.format("Required request parameter '%s' is missing", ex.getParameterName()))
                .path(request.getRequestURI())
                .correlationId(CorrelationContext.get())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<ErrorResponse> handleMissingPathVariable(MissingPathVariableException ex, HttpServletRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.name())
                .errorCode(ErrorCode.BAD_REQUEST.name())
                .message(String.format("Required path variable '%s' is missing", ex.getVariableName()))
                .path(request.getRequestURI())
                .correlationId(CorrelationContext.get())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex, HttpServletRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.name())
                .errorCode(ErrorCode.BAD_REQUEST.name())
                .message("Malformed JSON request or invalid field format")
                .path(request.getRequestURI())
                .correlationId(CorrelationContext.get())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.name())
                .errorCode(ErrorCode.BAD_REQUEST.name())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .correlationId(CorrelationContext.get())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }


    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException ex, HttpServletRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error(HttpStatus.NOT_FOUND.name())
                .errorCode(ErrorCode.BAD_REQUEST.name())
                .message(String.format("Resource '%s' not found", request.getRequestURI()))
                .path(request.getRequestURI())
                .correlationId(CorrelationContext.get())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(org.springframework.web.HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(HttpStatus.METHOD_NOT_ALLOWED.value())
                .error(HttpStatus.METHOD_NOT_ALLOWED.name())
                .errorCode(ErrorCode.BAD_REQUEST.name())
                .message(String.format("HTTP method '%s' is not supported for this endpoint", ex.getMethod()))
                .path(request.getRequestURI())
                .correlationId(CorrelationContext.get())
                .build();
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error while processing request: {}", request.getRequestURI(), ex);

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.name())
                .errorCode(ErrorCode.INTERNAL_SERVER_ERROR.name())
                .message("An unexpected error occurred")
                .path(request.getRequestURI())
                .correlationId(CorrelationContext.get())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
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
