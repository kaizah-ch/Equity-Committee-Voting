package com.equitycommittee.voting.api.dto.audit;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Builder
public record AuditLogResponse(
        UUID id,
        String entityType,
        UUID entityId,
        String action,
        UUID actorId,
        String actorEmail,
        Map<String, Object> metadata,
        LocalDateTime createdAt
) {
}
