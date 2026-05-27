package com.equitycommittee.voting.api.controller;

import com.equitycommittee.voting.api.dto.cases.CaseResponse;
import com.equitycommittee.voting.api.dto.cases.CreateCaseRequest;
import com.equitycommittee.voting.api.dto.cases.UpdateCaseRequest;
import com.equitycommittee.voting.api.dto.cases.UpdateCaseStatusRequest;
import com.equitycommittee.voting.domain.enums.CaseStatus;
import com.equitycommittee.voting.service.CaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
public class CaseController {

    private final CaseService caseService;

    @PostMapping
    public ResponseEntity<CaseResponse> createCase(@Valid @RequestBody CreateCaseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(caseService.createCase(request));
    }

    @GetMapping
    public ResponseEntity<Page<CaseResponse>> listCases(
            @RequestParam(required = false) CaseStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(caseService.listCases(status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CaseResponse> getCase(@PathVariable UUID id) {
        return ResponseEntity.ok(caseService.getCase(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CaseResponse> updateCase(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCaseRequest request) {
        return ResponseEntity.ok(caseService.updateCase(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<CaseResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCaseStatusRequest request) {
        return ResponseEntity.ok(caseService.updateStatus(id, request));
    }
}
