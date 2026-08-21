package com.roomsync.agent.entity;

import com.roomsync.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * AgentAction Entity representing individual sub-actions, tool calls, and prompt evaluations
 * within an agent operation trace.
 */
@Entity
@Table(name = "agent_actions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id")
    private String requestId;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "agent_run_id")
    private String agentRunId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_action_id")
    private AgentAction parentAction;

    @Column(name = "conversation_id")
    private String conversationId;

    @NotBlank
    @Column(name = "agent_name", nullable = false, length = 100)
    private String agentName;

    @Column(name = "agent_version", length = 50)
    private String agentVersion;

    @Column(name = "model_provider", length = 100)
    private String modelProvider;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "prompt_version", length = 50)
    private String promptVersion;

    @Column(name = "tool_schema_version", length = 50)
    private String toolSchemaVersion;

    @Column(name = "tool_name", length = 100)
    private String toolName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_arguments", columnDefinition = "jsonb")
    private Map<String, Object> toolArguments;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acting_user_id", nullable = false)
    private User actingUser;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operation_id", nullable = false)
    private AgentOperation operation;

    @Column(name = "confirmation_id")
    private String confirmationId;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @NotBlank
    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "result_reference")
    private String resultReference;
}
