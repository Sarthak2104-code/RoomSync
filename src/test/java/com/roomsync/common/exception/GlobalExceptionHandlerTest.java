package com.roomsync.common.exception;

import com.roomsync.common.filter.CorrelationContext;
import com.roomsync.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = Mockito.mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/test");
        CorrelationContext.set("corr-12345");
    }

    @AfterEach
    void tearDown() {
        CorrelationContext.clear();
    }

    @Test
    @DisplayName("Should map RoomSyncException to appropriate HTTP status, ErrorCode, and correlation ID")
    void shouldHandleRoomSyncException() {
        RoomSyncException ex = new RoomSyncException(ErrorCode.ROOM_NOT_FOUND, "Room 42 not found") {};

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleRoomSyncException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getError()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().getErrorCode()).isEqualTo("ROOM_NOT_FOUND");
        assertThat(response.getBody().getMessage()).isEqualTo("Room 42 not found");
        assertThat(response.getBody().getCorrelationId()).isEqualTo("corr-12345");
        assertThat(response.getBody().getPath()).isEqualTo("/api/test");
    }

    @Test
    @DisplayName("Should map PostgreSQL exclusion constraint 'no_overlapping_bookings' to 409 BOOKING_CONFLICT")
    void shouldHandlePostgreSQLExclusionConstraint() {
        ServerErrorMessage serverError = Mockito.mock(ServerErrorMessage.class);
        when(serverError.getConstraint()).thenReturn("no_overlapping_bookings");

        PSQLException psqlEx = new PSQLException(serverError);
        DataIntegrityViolationException dive = new DataIntegrityViolationException("Conflict", psqlEx);

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleDataIntegrityViolation(dive, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("BOOKING_CONFLICT");
        assertThat(response.getBody().getMessage()).isEqualTo("The room is already booked for the selected time.");
        assertThat(response.getBody().getCorrelationId()).isEqualTo("corr-12345");
    }

    @Test
    @DisplayName("Should map PostgreSQL unique constraint violations to 409 DUPLICATE_RESOURCE")
    void shouldHandlePostgreSQLUniqueConstraint() {
        ServerErrorMessage serverError = Mockito.mock(ServerErrorMessage.class);
        when(serverError.getConstraint()).thenReturn("ux_rooms_location_name_lower");

        PSQLException psqlEx = new PSQLException(serverError);
        DataIntegrityViolationException dive = new DataIntegrityViolationException("Unique error", psqlEx);

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleDataIntegrityViolation(dive, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("DUPLICATE_RESOURCE");
    }

    @Test
    @DisplayName("Should map unknown database integrity violations to 400 BAD_REQUEST safely")
    void shouldHandleUnknownDataIntegrityViolation() {
        DataIntegrityViolationException dive = new DataIntegrityViolationException("Generic foreign key error");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleDataIntegrityViolation(dive, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().getMessage()).isEqualTo("Data integrity constraint violation occurred");
    }

    @Test
    @DisplayName("Should map unexpected Exception to 500 INTERNAL_SERVER_ERROR without exposing internal details")
    void shouldHandleGenericException() {
        Exception ex = new RuntimeException("NullPointerException in internal SQL service");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getErrorCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().getCorrelationId()).isEqualTo("corr-12345");
    }
}
