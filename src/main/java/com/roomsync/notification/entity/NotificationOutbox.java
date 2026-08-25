package com.roomsync.notification.entity;

import com.roomsync.agent.entity.AgentOperation;
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
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * NotificationOutbox Entity representing transactional notification events to be dispatched reliably.
 */
@Entity
@Table(name = "notification_outbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "event_id", nullable = false, unique = true, length = 255)
    private String eventId;

    @NotBlank
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @NotBlank
    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @NotBlank
    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @NotBlank
    @Column(name = "recipient", nullable = false)
    private String recipient;

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> payload;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private NotificationStatus status;

    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "next_attempt_time")
    private OffsetDateTime nextAttemptTime;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operation_id")
    private AgentOperation operation;

    @jakarta.persistence.PrePersist
    public void prePersist() {
        if (this.eventId == null || this.eventId.trim().isEmpty()) {
            this.eventId = "evt_" + java.util.UUID.randomUUID().toString().replace("-", "");
        }
    }
}
