package com.equitycommittee.voting.service;

import com.equitycommittee.voting.api.dto.messages.MessageResponse;
import com.equitycommittee.voting.api.dto.messages.SendMessageRequest;
import com.equitycommittee.voting.domain.entity.CaseEntry;
import com.equitycommittee.voting.domain.entity.CaseMessage;
import com.equitycommittee.voting.domain.entity.User;
import com.equitycommittee.voting.domain.enums.CaseStatus;
import com.equitycommittee.voting.domain.enums.Role;
import com.equitycommittee.voting.domain.repository.CaseMessageRepository;
import com.equitycommittee.voting.domain.repository.CaseRepository;
import com.equitycommittee.voting.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final CaseMessageRepository messageRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(UUID caseId, Pageable pageable) {
        User actor = currentUser();
        CaseEntry caseEntry = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));
        assertCanAccessDiscussion(actor, caseEntry);

        return messageRepository.findByCaseEntryIdOrderByCreatedAtAsc(caseId, pageable)
                .map(MessageResponse::from);
    }

    @Transactional
    public MessageResponse sendMessage(UUID caseId, SendMessageRequest req) {
        User sender = currentUser();
        CaseEntry caseEntry = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));
        assertCanAccessDiscussion(sender, caseEntry);

        CaseMessage parent = null;
        if (req.parentMessageId() != null) {
            parent = messageRepository.findById(req.parentMessageId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent message not found"));
            if (parent.getCaseEntry() == null || !caseId.equals(parent.getCaseEntry().getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent message does not belong to this case");
            }
        }

        CaseMessage message = CaseMessage.builder()
                .caseEntry(caseEntry)
                .sender(sender)
                .messageText(req.messageText())
                .parentMessage(parent)
                .build();

        message = messageRepository.saveAndFlush(message);
        auditService.log("MESSAGE", message.getId(), "SENT", sender, Map.of("caseId", caseId.toString()));

        MessageResponse response = MessageResponse.from(message);
        messagingTemplate.convertAndSend("/topic/cases/" + caseId + "/messages", response);
        return response;
    }

    private User currentUser() {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private void assertCanAccessDiscussion(User actor, CaseEntry caseEntry) {
        boolean isCaseCreator = caseEntry.getCreatedBy() != null
                && caseEntry.getCreatedBy().getId() != null
                && caseEntry.getCreatedBy().getId().equals(actor.getId());
        boolean isPrivilegedRole = actor.getRole() == Role.ADMIN || actor.getRole() == Role.CHAIRPERSON;
        if (isCaseCreator || isPrivilegedRole) {
            return;
        }

        if (caseEntry.getStatus() == CaseStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to access discussion for draft case");
        }

        boolean isCommitteeRole = actor.getRole() == Role.COMMITTEE_MEMBER || actor.getRole() == Role.SECRETARY;
        if (!isCommitteeRole) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to access case discussion");
        }
    }
}
