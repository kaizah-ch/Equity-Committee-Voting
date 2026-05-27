package com.equitycommittee.voting.domain.repository;

import com.equitycommittee.voting.domain.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    Page<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
        String entityType, UUID entityId, Pageable pageable);

    @Query(
            value = """
                    SELECT * FROM audit_logs a
                    WHERE (a.entity_type = 'CASE' AND a.entity_id = CAST(:caseId AS uuid))
                       OR (a.metadata ->> 'caseId' = :caseId)
                    ORDER BY a.created_at DESC
                    """,
            countQuery = """
                    SELECT COUNT(*) FROM audit_logs a
                    WHERE (a.entity_type = 'CASE' AND a.entity_id = CAST(:caseId AS uuid))
                       OR (a.metadata ->> 'caseId' = :caseId)
                    """,
            nativeQuery = true
    )
    Page<AuditLog> findCaseAuditTrail(@Param("caseId") String caseId, Pageable pageable);
}
