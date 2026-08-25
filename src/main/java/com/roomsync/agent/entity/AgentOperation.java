package com.roomsync.agent.entity;

import com.roomsync.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * AgentOperation Entity representing durable operations initiated by users or AI agents.
 */
@Entity
@Table(name = "agent_operations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "operation_id", nullable = false, unique = true, length = 255)
    private String operationId;

    @NotBlank
    @Column(name = "operation_type", nullable = false, length = 100)
    private String operationType;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acting_user_id", nullable = false)
    private User actingUser;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private AgentOperationStatus status;

    @Column(name = "resource_type", length = 100)
    private String resourceType;

    @Column(name = "resource_id", length = 255)
    private String resourceId;

    @Column(name = "request_hash", length = 255)
    private String requestHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_payload", columnDefinition = "jsonb")
    private Map<String, Object> resultPayload;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft_payload", columnDefinition = "jsonb")
    private Map<String, Object> draftPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_fields", columnDefinition = "jsonb")
    private Map<String, Object> missingFields;

    @Column(name = "canonical_action_hash")
    private String canonicalActionHash;

    @Column(name = "confirmation_id")
    private String confirmationId;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "result_reference")
    private String resultReference;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;
}
