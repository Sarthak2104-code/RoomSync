package com.roomsync.reliability.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomsync.agent.entity.AgentOperation;
import com.roomsync.agent.entity.AgentOperationStatus;
import com.roomsync.reliability.exception.IdempotencyConflictException;
import com.roomsync.reliability.exception.IdempotencyKeyRequiredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private RequestFingerprintService fingerprintService;

    @Mock
    private IdempotencyRegistrationService registrationService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private IdempotencyService idempotencyService;

    private final Long userId = 10L;

    @Test
    @DisplayName("1. Same key + same payload -> first execution runs business logic and marks SUCCEEDED")
    void testSameKeySamePayloadSuccess() {
        when(fingerprintService.generateFingerprint(any(), any(), any(), any())).thenReturn("hash_123");

        AgentOperation op = AgentOperation.builder()
                .operationId("op_100")
                .status(AgentOperationStatus.EXECUTING)
                .build();

        when(registrationService.startOrCheckOperation(eq(userId), eq("KEY-1"), eq("BOOKING_CREATE"), eq("hash_123"), any()))
                .thenReturn(IdempotencyRegistrationService.IdempotencyCheckResult.builder()
                        .isReplay(false)
                        .operation(op)
                        .build());

        AtomicInteger callCount = new AtomicInteger(0);
        String result = idempotencyService.executeIdempotent(
                userId, "KEY-1", "BOOKING_CREATE", "POST", "/api/bookings", Map.of("roomId", 1), String.class,
                () -> {
                    callCount.incrementAndGet();
                    return "BookingCreated";
                }
        );

        assertThat(result).isEqualTo("BookingCreated");
        assertThat(callCount.get()).isEqualTo(1);
        verify(registrationService).markOperationSucceeded(eq("op_100"), eq("CREATE"), any(), any());
    }

    @Test
    @DisplayName("2. Same key + different payload -> throws 409 IDEMPOTENCY_CONFLICT")
    void testSameKeyDifferentPayloadConflict() {
        when(fingerprintService.generateFingerprint(any(), any(), any(), any())).thenReturn("hash_456");

        when(registrationService.startOrCheckOperation(eq(userId), eq("KEY-1"), eq("BOOKING_CREATE"), eq("hash_456"), any()))
                .thenThrow(new IdempotencyConflictException("Idempotency key has already been used with a different request payload"));

        assertThatThrownBy(() -> idempotencyService.executeIdempotent(
                userId, "KEY-1", "BOOKING_CREATE", "POST", "/api/bookings", Map.of("roomId", 2), String.class, () -> "res"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    @DisplayName("3. Different keys -> independent operations")
    void testDifferentKeysIndependent() {
        when(fingerprintService.generateFingerprint(any(), any(), any(), any())).thenReturn("hash_key1", "hash_key2");

        AgentOperation op1 = AgentOperation.builder().operationId("op_1").status(AgentOperationStatus.EXECUTING).build();
        AgentOperation op2 = AgentOperation.builder().operationId("op_2").status(AgentOperationStatus.EXECUTING).build();

        when(registrationService.startOrCheckOperation(eq(userId), eq("KEY-1"), any(), eq("hash_key1"), any()))
                .thenReturn(IdempotencyRegistrationService.IdempotencyCheckResult.builder().isReplay(false).operation(op1).build());

        when(registrationService.startOrCheckOperation(eq(userId), eq("KEY-2"), any(), eq("hash_key2"), any()))
                .thenReturn(IdempotencyRegistrationService.IdempotencyCheckResult.builder().isReplay(false).operation(op2).build());

        String res1 = idempotencyService.executeIdempotent(userId, "KEY-1", "BOOKING_CREATE", "POST", "/api/bookings", Map.of("id", 1), String.class, () -> "res1");
        String res2 = idempotencyService.executeIdempotent(userId, "KEY-2", "BOOKING_CREATE", "POST", "/api/bookings", Map.of("id", 2), String.class, () -> "res2");

        assertThat(res1).isEqualTo("res1");
        assertThat(res2).isEqualTo("res2");
        verify(registrationService).markOperationSucceeded(eq("op_1"), any(), any(), any());
        verify(registrationService).markOperationSucceeded(eq("op_2"), any(), any(), any());
    }

    @Test
    @DisplayName("4. Missing key -> throws IdempotencyKeyRequiredException")
    void testMissingKeyThrowsException() {
        assertThatThrownBy(() -> idempotencyService.executeIdempotent(
                userId, null, "BOOKING_CREATE", "POST", "/api/bookings", Map.of("key", "val"), String.class, () -> "res"))
                .isInstanceOf(IdempotencyKeyRequiredException.class);

        assertThatThrownBy(() -> idempotencyService.executeIdempotent(
                userId, "   ", "BOOKING_CREATE", "POST", "/api/bookings", Map.of("key", "val"), String.class, () -> "res"))
                .isInstanceOf(IdempotencyKeyRequiredException.class);
    }

    @Test
    @DisplayName("5. Existing SUCCEEDED operation -> replay result without running business logic")
    void testReplaySucceededOperation() {
        when(fingerprintService.generateFingerprint(any(), any(), any(), any())).thenReturn("hash_123");

        AgentOperation op = AgentOperation.builder()
                .operationId("op_100")
                .status(AgentOperationStatus.SUCCEEDED)
                .resultPayload(Map.of("message", "SuccessCached"))
                .build();

        when(registrationService.startOrCheckOperation(eq(userId), eq("KEY-1"), eq("BOOKING_CREATE"), eq("hash_123"), any()))
                .thenReturn(IdempotencyRegistrationService.IdempotencyCheckResult.builder()
                        .isReplay(true)
                        .operation(op)
                        .build());

        AtomicInteger callCount = new AtomicInteger(0);
        Map result = idempotencyService.executeIdempotent(
                userId, "KEY-1", "BOOKING_CREATE", "POST", "/api/bookings", Map.of("roomId", 1), Map.class,
                () -> {
                    callCount.incrementAndGet();
                    return Map.of("message", "ShouldNotRun");
                }
        );

        assertThat(result).containsEntry("message", "SuccessCached");
        assertThat(callCount.get()).isEqualTo(0);
        verify(registrationService, never()).markOperationSucceeded(any(), any(), any(), any());
    }

    @Test
    @DisplayName("6. Business exception -> marks operation FAILED and rethrows exception")
    void testBusinessExceptionMarksFailed() {
        when(fingerprintService.generateFingerprint(any(), any(), any(), any())).thenReturn("hash_123");

        AgentOperation op = AgentOperation.builder()
                .operationId("op_err")
                .status(AgentOperationStatus.EXECUTING)
                .build();

        when(registrationService.startOrCheckOperation(eq(userId), eq("KEY-ERR"), eq("BOOKING_CREATE"), eq("hash_123"), any()))
                .thenReturn(IdempotencyRegistrationService.IdempotencyCheckResult.builder()
                        .isReplay(false)
                        .operation(op)
                        .build());

        assertThatThrownBy(() -> idempotencyService.executeIdempotent(
                userId, "KEY-ERR", "BOOKING_CREATE", "POST", "/api/bookings", Map.of("roomId", 1), String.class,
                () -> {
                    throw new RuntimeException("DB down");
                }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB down");

        verify(registrationService).markOperationFailed(eq("op_err"), eq("INTERNAL_SERVER_ERROR"), eq("DB down"));
    }
}
