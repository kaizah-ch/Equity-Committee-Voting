package com.equitycommittee.voting.api.controller;

import com.equitycommittee.voting.api.dto.audit.AuditLogResponse;
import com.equitycommittee.voting.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/cases/{caseId}/audit-logs")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<Page<AuditLogResponse>> getCaseAuditTrail(
            @PathVariable UUID caseId,
            @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(auditService.getCaseAuditTrail(caseId, pageable));
    }
}
