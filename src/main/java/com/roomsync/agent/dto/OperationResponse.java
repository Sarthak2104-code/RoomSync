package com.roomsync.agent.dto;

import com.roomsync.agent.entity.AgentOperation;
import com.roomsync.agent.entity.AgentOperationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperationResponse {

    private String operationId;
    private String operationType;
    private Long actingUserId;
    private AgentOperationStatus status;
    private String resourceType;
    private String resourceId;
    private String requestHash;
    private Map<String, Object> result;
    private String errorCode;
    private String errorMessage;
    private String correlationId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime expiresAt;

    public static OperationResponse fromEntity(AgentOperation operation) {
        if (operation == null) return null;
        return OperationResponse.builder()
                .operationId(operation.getOperationId())
                .operationType(operation.getOperationType())
                .actingUserId(operation.getActingUser() != null ? operation.getActingUser().getId() : null)
                .status(operation.getStatus())
                .resourceType(operation.getResourceType())
                .resourceId(operation.getResourceId())
                .requestHash(operation.getRequestHash())
                .result(operation.getResultPayload())
                .errorCode(operation.getErrorCode())
                .errorMessage(operation.getErrorMessage())
                .correlationId(operation.getCorrelationId())
                .createdAt(operation.getCreatedAt())
                .updatedAt(operation.getUpdatedAt())
                .expiresAt(operation.getExpiresAt())
                .build();
    }
}
