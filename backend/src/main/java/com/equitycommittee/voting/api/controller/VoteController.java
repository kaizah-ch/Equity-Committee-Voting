package com.equitycommittee.voting.api.controller;

import com.equitycommittee.voting.api.dto.voting.VoteRequest;
import com.equitycommittee.voting.api.dto.voting.VoteResponse;
import com.equitycommittee.voting.service.VotingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cases/{caseId}/vote")
@RequiredArgsConstructor
public class VoteController {

    private final VotingService votingService;

    @PostMapping
    public ResponseEntity<VoteResponse> castVote(
            @PathVariable UUID caseId,
            @Valid @RequestBody VoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(votingService.castVote(caseId, request));
    }

    @GetMapping
    public ResponseEntity<List<VoteResponse>> getVotes(@PathVariable UUID caseId) {
        return ResponseEntity.ok(votingService.getVotes(caseId));
    }
}
