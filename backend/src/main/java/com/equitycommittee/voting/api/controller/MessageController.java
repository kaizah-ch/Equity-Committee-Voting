package com.equitycommittee.voting.api.controller;

import com.equitycommittee.voting.api.dto.messages.MessageResponse;
import com.equitycommittee.voting.api.dto.messages.SendMessageRequest;
import com.equitycommittee.voting.service.MessageService;
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
@RequestMapping("/api/cases/{caseId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    public ResponseEntity<Page<MessageResponse>> getMessages(
            @PathVariable UUID caseId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(messageService.getMessages(caseId, pageable));
    }

    @PostMapping
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable UUID caseId,
            @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(messageService.sendMessage(caseId, request));
    }
}
