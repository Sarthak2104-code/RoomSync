package com.roomsync.reliability.service;

import com.roomsync.agent.entity.AgentAction;
import com.roomsync.agent.entity.AgentOperation;
import com.roomsync.agent.entity.AgentOperationStatus;
import com.roomsync.agent.repository.AgentActionRepository;
import com.roomsync.agent.repository.AgentOperationRepository;
import com.roomsync.common.filter.CorrelationContext;
import com.roomsync.common.time.DateTimeProvider;
import com.roomsync.reliability.entity.IdempotencyRecord;
import com.roomsync.reliability.exception.IdempotencyConflictException;
import com.roomsync.reliability.repository.IdempotencyRecordRepository;
import com.roomsync.user.entity.User;
import com.roomsync.user.repository.UserRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyRegistrationService {

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final AgentOperationRepository agentOperationRepository;
    private final AgentActionRepository agentActionRepository;
    private final UserRepository userRepository;
    private final DateTimeProvider dateTimeProvider;

    @Getter
    @Builder
    public static class IdempotencyCheckResult {
        private final boolean isReplay;
        private final AgentOperation operation;
        private final IdempotencyRecord record;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyCheckResult startOrCheckOperation(
            Long userId,
            String idempotencyKey,
            String operationType,
            String requestHash,
            Map<String, Object> draftPayload) {

        User user = userRepository.findById(userId).orElse(null);
        OffsetDateTime now = dateTimeProvider.nowOffsetDateTime();
        OffsetDateTime expiresAt = now.plusHours(24);
        String correlationId = CorrelationContext.get();
        String operationId = "op_" + UUID.randomUUID().toString().replace("-", "");

        try {
            // Create AgentOperation
            AgentOperation operation = AgentOperation.builder()
                    .operationId(operationId)
                    .operationType(operationType)
                    .actingUser(user)
                    .status(AgentOperationStatus.EXECUTING)
                    .requestHash(requestHash)
                    .draftPayload(draftPayload)
                    .idempotencyKey(idempotencyKey)
                    .correlationId(correlationId)
                    .expiresAt(expiresAt)
                    .build();
            AgentOperation savedOp = agentOperationRepository.saveAndFlush(operation);

            // Create IdempotencyRecord
            IdempotencyRecord record = IdempotencyRecord.builder()
                    .scopeUser(user)
                    .idempotencyKey(idempotencyKey)
                    .operationType(operationType)
                    .requestHash(requestHash)
                    .operation(savedOp)
                    .status("EXECUTING")
                    .expiresAt(expiresAt)
                    .build();
            IdempotencyRecord savedRecord = idempotencyRecordRepository.saveAndFlush(record);

            // Create AgentAction
            AgentAction action = AgentAction.builder()
                    .requestId(correlationId)
                    .traceId(correlationId)
                    .agentName("RoomSync-Core")
                    .actingUser(user)
                    .operation(savedOp)
                    .idempotencyKey(idempotencyKey)
                    .startedAt(now)
                    .status("EXECUTING")
                    .build();
            agentActionRepository.saveAndFlush(action);

            log.info("Registered new idempotent operation id: {} key: {} for user: {}",
                    savedOp.getOperationId(), idempotencyKey, userId);

            return IdempotencyCheckResult.builder()
                    .isReplay(false)
                    .operation(savedOp)
                    .record(savedRecord)
                    .build();

        } catch (DataIntegrityViolationException ex) {
            log.info("Concurrent idempotency record conflict detected for user: {} key: {}. Reloading existing record...",
                    userId, idempotencyKey);

            IdempotencyRecord existing = idempotencyRecordRepository
                    .findByScopeUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .orElseThrow(() -> ex);

            if (!existing.getRequestHash().equals(requestHash)) {
                log.warn("Idempotency conflict: key '{}' already used with different request hash (existing: {}, incoming: {})",
                        idempotencyKey, existing.getRequestHash(), requestHash);
                throw new IdempotencyConflictException("Idempotency key has already been used with a different request payload");
            }

            log.info("Idempotent replay matched existing operation: {} for user: {} key: {}",
                    existing.getOperation() != null ? existing.getOperation().getOperationId() : "N/A",
                    userId, idempotencyKey);

            return IdempotencyCheckResult.builder()
                    .isReplay(true)
                    .operation(existing.getOperation())
                    .record(existing)
                    .build();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markOperationSucceeded(
            String operationId,
            String resourceType,
            String resourceId,
            Map<String, Object> resultPayload) {

        AgentOperation operation = agentOperationRepository.findByOperationId(operationId).orElse(null);
        if (operation != null) {
            operation.setStatus(AgentOperationStatus.SUCCEEDED);
            operation.setResourceType(resourceType);
            operation.setResourceId(resourceId);
            operation.setResultPayload(resultPayload);
            operation.setResultReference("/api/" + resourceType.toLowerCase() + "s/" + resourceId);
            agentOperationRepository.saveAndFlush(operation);

            if (operation.getIdempotencyKey() != null && operation.getActingUser() != null) {
                idempotencyRecordRepository
                        .findByScopeUserIdAndIdempotencyKey(operation.getActingUser().getId(), operation.getIdempotencyKey())
                        .ifPresent(rec -> {
                            rec.setStatus("SUCCEEDED");
                            rec.setResponsePayload(resultPayload);
                            rec.setResponseReference("/api/" + resourceType.toLowerCase() + "s/" + resourceId);
                            idempotencyRecordRepository.saveAndFlush(rec);
                        });
            }

            agentActionRepository.findAllByOperationId(operation.getId()).forEach(action -> {
                action.setStatus("SUCCEEDED");
                action.setCompletedAt(dateTimeProvider.nowOffsetDateTime());
                if (action.getStartedAt() != null) {
                    action.setLatencyMs(java.time.Duration.between(action.getStartedAt(), action.getCompletedAt()).toMillis());
                }
                action.setResultReference("/api/" + resourceType.toLowerCase() + "s/" + resourceId);
                agentActionRepository.saveAndFlush(action);
            });

            log.info("Marked operation id: {} as SUCCEEDED", operationId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markOperationFailed(String operationId, String errorCode, String errorMessage) {
        AgentOperation operation = agentOperationRepository.findByOperationId(operationId).orElse(null);
        if (operation != null) {
            operation.setStatus(AgentOperationStatus.FAILED);
            operation.setErrorCode(errorCode);
            operation.setErrorMessage(errorMessage);
            agentOperationRepository.saveAndFlush(operation);

            if (operation.getIdempotencyKey() != null && operation.getActingUser() != null) {
                idempotencyRecordRepository
                        .findByScopeUserIdAndIdempotencyKey(operation.getActingUser().getId(), operation.getIdempotencyKey())
                        .ifPresent(rec -> {
                            rec.setStatus("FAILED");
                            idempotencyRecordRepository.saveAndFlush(rec);
                        });
            }

            agentActionRepository.findAllByOperationId(operation.getId()).forEach(action -> {
                action.setStatus("FAILED");
                action.setErrorCode(errorCode);
                action.setCompletedAt(dateTimeProvider.nowOffsetDateTime());
                if (action.getStartedAt() != null) {
                    action.setLatencyMs(java.time.Duration.between(action.getStartedAt(), action.getCompletedAt()).toMillis());
                }
                agentActionRepository.saveAndFlush(action);
            });

            log.info("Marked operation id: {} as FAILED ({})", operationId, errorCode);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markOperationTimeoutUnknown(String operationId) {
        AgentOperation operation = agentOperationRepository.findByOperationId(operationId).orElse(null);
        if (operation != null) {
            operation.setStatus(AgentOperationStatus.TIMEOUT_UNKNOWN);
            agentOperationRepository.saveAndFlush(operation);
            log.info("Marked operation id: {} as TIMEOUT_UNKNOWN", operationId);
        }
    }
}
