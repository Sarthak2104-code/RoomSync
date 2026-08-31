package com.roomsync.audit.dto;

import com.roomsync.audit.entity.AuditLog;
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
public class AuditLogResponse {
    private Long id;
    private Long actorUserId;
    private String actorWissenId;
    private String actorName;
    private String actorEmail;
    private Long affectedUserId;
    private String affectedUserWissenId;
    private String affectedUserName;
    private String affectedUserEmail;
    private String action;
    private String entityType;
    private String entityId;
    private Long locationId;
    private String locationName;
    private Long roomId;
    private String roomName;
    private Long bookingId;
    private String result;
    private String correlationId;
    private OffsetDateTime createdAt;

    public static AuditLogResponse fromEntity(AuditLog entity) {
        if (entity == null) {
            return null;
        }

        String resultVal = null;
        String correlationIdVal = null;
        Map<String, Object> metadata = entity.getMetadata();
        if (metadata != null) {
            if (metadata.containsKey("result")) {
                resultVal = String.valueOf(metadata.get("result"));
            } else if (metadata.containsKey("status")) {
                resultVal = String.valueOf(metadata.get("status"));
            }
            if (metadata.containsKey("correlationId")) {
                correlationIdVal = String.valueOf(metadata.get("correlationId"));
            } else if (metadata.containsKey("traceId")) {
                correlationIdVal = String.valueOf(metadata.get("traceId"));
            }
        }

        String actorName = null;
        String actorEmail = null;
        String actorWissenId = null;
        if (entity.getActorUser() != null) {
            actorName = entity.getActorUser().getName();
            actorEmail = entity.getActorUser().getEmail();
            actorWissenId = entity.getActorUser().getWissenId();
        } else if (metadata != null && metadata.containsKey("actorType")) {
            actorName = String.valueOf(metadata.get("actorType"));
        } else {
            actorName = "SYSTEM";
        }

        String affectedUserName = null;
        String affectedUserEmail = null;
        String affectedUserWissenId = null;
        if (entity.getAffectedUser() != null) {
            affectedUserName = entity.getAffectedUser().getName();
            affectedUserEmail = entity.getAffectedUser().getEmail();
            affectedUserWissenId = entity.getAffectedUser().getWissenId();
        }

        return AuditLogResponse.builder()
                .id(entity.getId())
                .actorUserId(entity.getActorUser() != null ? entity.getActorUser().getId() : null)
                .actorWissenId(actorWissenId)
                .actorName(actorName)
                .actorEmail(actorEmail)
                .affectedUserId(entity.getAffectedUser() != null ? entity.getAffectedUser().getId() : null)
                .affectedUserWissenId(affectedUserWissenId)
                .affectedUserName(affectedUserName)
                .affectedUserEmail(affectedUserEmail)
                .action(entity.getAction())
                .entityType(entity.getEntityType())
                .entityId(entity.getEntityId())
                .locationId(entity.getLocation() != null ? entity.getLocation().getId() : null)
                .locationName(entity.getLocation() != null ? entity.getLocation().getName() : null)
                .roomId(entity.getRoom() != null ? entity.getRoom().getId() : null)
                .roomName(entity.getRoom() != null ? entity.getRoom().getName() : null)
                .bookingId(entity.getBooking() != null ? entity.getBooking().getId() : null)
                .result(resultVal)
                .correlationId(correlationIdVal)
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
