package com.roomsync.reliability.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomsync.agent.entity.AgentOperation;
import com.roomsync.agent.entity.AgentOperationStatus;
import com.roomsync.common.exception.RoomSyncException;
import com.roomsync.reliability.exception.IdempotencyKeyRequiredException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final RequestFingerprintService fingerprintService;
    private final IdempotencyRegistrationService registrationService;
    private final ObjectMapper objectMapper;

    public <T> T executeIdempotent(
            Long userId,
            String idempotencyKey,
            String operationType,
            String httpMethod,
            String path,
            Object requestPayload,
            Class<T> responseType,
            Supplier<T> businessLogic) {

        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new IdempotencyKeyRequiredException("Idempotency-Key header is required for this operation");
        }

        String trimmedKey = idempotencyKey.trim();
        String requestHash = fingerprintService.generateFingerprint(httpMethod, path, userId, requestPayload);
        Map<String, Object> draftPayload = convertToMap(requestPayload);

        IdempotencyRegistrationService.IdempotencyCheckResult checkResult = registrationService.startOrCheckOperation(
                userId, trimmedKey, operationType, requestHash, draftPayload
        );

        AgentOperation operation = checkResult.getOperation();

        if (checkResult.isReplay()) {
            if (operation != null && operation.getStatus() == AgentOperationStatus.SUCCEEDED && operation.getResultPayload() != null) {
                log.info("Replaying cached result for operation id: {} key: {}", operation.getOperationId(), trimmedKey);
                T cached = objectMapper.convertValue(operation.getResultPayload(), responseType);
                if (cached instanceof com.roomsync.booking.dto.BookingResponse br && operation.getOperationId() != null) {
                    br.setOperationId(operation.getOperationId());
                } else if (cached instanceof com.roomsync.booking.dto.RecurringOccurrenceResult ror && operation.getOperationId() != null) {
                    ror.setOperationId(operation.getOperationId());
                }
                return cached;
            }
            if (checkResult.getRecord() != null && checkResult.getRecord().getResponsePayload() != null) {
                return objectMapper.convertValue(checkResult.getRecord().getResponsePayload(), responseType);
            }
            if (operation != null && operation.getStatus() == AgentOperationStatus.EXECUTING) {
                log.info("Operation id: {} is currently EXECUTING for key: {}", operation.getOperationId(), trimmedKey);
            }
        }

        String opId = operation != null ? operation.getOperationId() : null;

        try {
            T result = businessLogic.get();
            if (opId != null) {
                if (result instanceof com.roomsync.booking.dto.BookingResponse br) {
                    br.setOperationId(opId);
                } else if (result instanceof com.roomsync.booking.dto.RecurringOccurrenceResult ror) {
                    ror.setOperationId(opId);
                }
                Map<String, Object> resultMap = convertToMap(result);
                String resourceId = extractResourceId(result);
                registrationService.markOperationSucceeded(opId, operationType.replace("BOOKING_", ""), resourceId, resultMap);
            }
            return result;

        } catch (Exception ex) {
            log.error("Idempotent operation id: {} failed during business execution", opId, ex);
            if (opId != null) {
                String errorCode = (ex instanceof RoomSyncException rse && rse.getErrorCode() != null)
                        ? rse.getErrorCode().name() : "INTERNAL_SERVER_ERROR";
                registrationService.markOperationFailed(opId, errorCode, ex.getMessage());
            }
            throw ex;
        }
    }

    private Map<String, Object> convertToMap(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.convertValue(obj, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of("data", obj.toString());
        }
    }

    private String extractResourceId(Object result) {
        if (result == null) return "UNKNOWN";
        try {
            JsonNode node = objectMapper.valueToTree(result);
            if (node.has("id")) {
                return node.get("id").asText();
            }
        } catch (Exception ignored) {}
        return "UNKNOWN";
    }
}
