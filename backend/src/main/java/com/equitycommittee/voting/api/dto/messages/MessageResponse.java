package com.equitycommittee.voting.api.dto.messages;

import com.equitycommittee.voting.domain.entity.CaseMessage;

import java.time.LocalDateTime;
import java.util.UUID;

public record MessageResponse(
    UUID id,
    UUID caseId,
    UUID senderId,
    String senderName,
    String messageText,
    UUID parentMessageId,
    LocalDateTime createdAt
) {
    public static MessageResponse from(CaseMessage m) {
        return new MessageResponse(
            m.getId(), m.getCaseEntry().getId(),
            m.getSender().getId(), m.getSender().getFullName(),
            m.getMessageText(),
            m.getParentMessage() != null ? m.getParentMessage().getId() : null,
            m.getCreatedAt()
        );
    }
}
