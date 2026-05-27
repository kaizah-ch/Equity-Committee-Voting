package com.equitycommittee.voting.service;

import com.equitycommittee.voting.api.dto.audit.AuditLogResponse;
import com.equitycommittee.voting.domain.entity.AuditLog;
import com.equitycommittee.voting.domain.entity.User;
import com.equitycommittee.voting.domain.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async
    public void log(String entityType, UUID entityId, String action, User actor, Map<String, Object> metadata) {
        AuditLog log = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .actor(actor)
                .metadata(metadata)
                .build();
        auditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getCaseAuditTrail(UUID caseId, Pageable pageable) {
        return auditLogRepository.findCaseAuditTrail(caseId.toString(), pageable)
                .map(this::toResponse);
    }

    private AuditLogResponse toResponse(AuditLog auditLog) {
        return AuditLogResponse.builder()
                .id(auditLog.getId())
                .entityType(auditLog.getEntityType())
                .entityId(auditLog.getEntityId())
                .action(auditLog.getAction())
                .actorId(auditLog.getActor().getId())
                .actorEmail(auditLog.getActor().getEmail())
                .metadata(auditLog.getMetadata())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }
}
