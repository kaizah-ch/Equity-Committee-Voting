package com.equitycommittee.voting.service;

import com.equitycommittee.voting.api.dto.messages.SendMessageRequest;
import com.equitycommittee.voting.domain.entity.CaseEntry;
import com.equitycommittee.voting.domain.entity.CaseMessage;
import com.equitycommittee.voting.domain.entity.User;
import com.equitycommittee.voting.domain.enums.CaseStatus;
import com.equitycommittee.voting.domain.enums.Role;
import com.equitycommittee.voting.domain.repository.CaseMessageRepository;
import com.equitycommittee.voting.domain.repository.CaseRepository;
import com.equitycommittee.voting.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceAuthTest {

    @Mock
    private CaseMessageRepository messageRepository;
    @Mock
    private CaseRepository caseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(
                messageRepository,
                caseRepository,
                userRepository,
                auditService,
                messagingTemplate
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getMessages_forbiddenForUnrelatedCommitteeMemberOnDraftCase() {
        UUID actorId = UUID.randomUUID();
        User actor = user(actorId, Role.COMMITTEE_MEMBER);
        User creator = user(UUID.randomUUID(), Role.COMMITTEE_MEMBER);
        CaseEntry draftCase = caseEntry(UUID.randomUUID(), CaseStatus.DRAFT, creator);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null)
        );
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(caseRepository.findById(draftCase.getId())).thenReturn(Optional.of(draftCase));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> messageService.getMessages(draftCase.getId(), PageRequest.of(0, 20)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(messageRepository, never()).findByCaseEntryIdOrderByCreatedAtAsc(draftCase.getId(), PageRequest.of(0, 20));
    }

    @Test
    void sendMessage_rejectsParentMessageFromDifferentCase() {
        UUID actorId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        User actor = user(actorId, Role.COMMITTEE_MEMBER);
        User creator = user(UUID.randomUUID(), Role.COMMITTEE_MEMBER);
        CaseEntry caseEntry = caseEntry(caseId, CaseStatus.SUBMITTED, creator);
        CaseEntry otherCase = caseEntry(UUID.randomUUID(), CaseStatus.SUBMITTED, creator);
        CaseMessage parent = CaseMessage.builder()
                .id(parentId)
                .caseEntry(otherCase)
                .sender(actor)
                .messageText("parent")
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null)
        );
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseEntry));
        when(messageRepository.findById(parentId)).thenReturn(Optional.of(parent));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> messageService.sendMessage(caseId, new SendMessageRequest("reply", parentId)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(messageRepository, never()).saveAndFlush(any(CaseMessage.class));
    }

    @Test
    void sendMessage_returnsFlushedCreatedAt() {
        UUID actorId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        User actor = user(actorId, Role.COMMITTEE_MEMBER);
        CaseEntry caseEntry = caseEntry(caseId, CaseStatus.SUBMITTED, actor);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null)
        );
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseEntry));
        when(messageRepository.saveAndFlush(any(CaseMessage.class))).thenAnswer(invocation -> {
            CaseMessage message = invocation.getArgument(0);
            message.setId(UUID.randomUUID());
            message.setCreatedAt(LocalDateTime.now());
            return message;
        });

        var response = messageService.sendMessage(caseId, new SendMessageRequest("hello", null));

        assertNotNull(response.createdAt());
        verify(messageRepository).saveAndFlush(any(CaseMessage.class));
        verify(messagingTemplate).convertAndSend("/topic/cases/" + caseId + "/messages", response);
    }

    private static User user(UUID id, Role role) {
        return User.builder()
                .id(id)
                .email(id + "@equity.com")
                .password("secret")
                .fullName("User")
                .role(role)
                .build();
    }

    private static CaseEntry caseEntry(UUID id, CaseStatus status, User createdBy) {
        return CaseEntry.builder()
                .id(id)
                .referenceNumber("ECV-" + id.toString().substring(0, 8))
                .clientName("Client")
                .requestedAmount(BigDecimal.ONE)
                .productType("TERM_LOAN")
                .status(status)
                .createdBy(createdBy)
                .build();
    }
}
