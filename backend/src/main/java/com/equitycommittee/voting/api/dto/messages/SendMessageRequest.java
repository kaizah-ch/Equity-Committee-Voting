package com.equitycommittee.voting.api.dto.messages;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record SendMessageRequest(
    @NotBlank String messageText,
    UUID parentMessageId
) {}
