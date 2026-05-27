package com.equitycommittee.voting.service;

import com.equitycommittee.voting.api.dto.cases.CaseResponse;
import com.equitycommittee.voting.api.dto.cases.CreateCaseRequest;
import com.equitycommittee.voting.api.dto.cases.UpdateCaseRequest;
import com.equitycommittee.voting.domain.entity.CaseEntry;
import com.equitycommittee.voting.domain.entity.User;
import com.equitycommittee.voting.domain.enums.CaseStatus;
import com.equitycommittee.voting.domain.enums.Role;
import com.equitycommittee.voting.domain.repository.CaseRepository;
import com.equitycommittee.voting.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
class CaseServiceAuthTest {

    @Mock
    private CaseRepository caseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private CaseService caseService;

    @BeforeEach
    void setUp() {
        caseService = new CaseService(caseRepository, userRepository, auditService, notificationService, messagingTemplate);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listCases_committeeWithoutStatus_usesNonDraftOrOwnQuery() {
        UUID actorId = UUID.randomUUID();
        User actor = user(actorId, Role.COMMITTEE_MEMBER);
        PageRequest pageable = PageRequest.of(0, 20);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null)
        );
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(caseRepository.findByStatusNotOrCreatedById(CaseStatus.DRAFT, actorId, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        caseService.listCases(null, pageable);

        verify(caseRepository).findByStatusNotOrCreatedById(CaseStatus.DRAFT, actorId, pageable);
        verify(caseRepository, never()).findAll(pageable);
    }

    @Test
    void listCases_committeeDraftFilter_usesOwnDraftQuery() {
        UUID actorId = UUID.randomUUID();
        User actor = user(actorId, Role.COMMITTEE_MEMBER);
        PageRequest pageable = PageRequest.of(0, 20);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null)
        );
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(caseRepository.findByStatusAndCreatedById(CaseStatus.DRAFT, actorId, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        caseService.listCases(CaseStatus.DRAFT, pageable);

        verify(caseRepository).findByStatusAndCreatedById(CaseStatus.DRAFT, actorId, pageable);
        verify(caseRepository, never()).findByStatus(CaseStatus.DRAFT, pageable);
    }

    @Test
    void getCase_forbiddenForSecretaryOnDraftCaseNotOwned() {
        UUID actorId = UUID.randomUUID();
        User actor = user(actorId, Role.SECRETARY);
        User creator = user(UUID.randomUUID(), Role.COMMITTEE_MEMBER);
        CaseEntry draftCase = caseEntry(UUID.randomUUID(), CaseStatus.DRAFT, creator);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null)
        );
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(caseRepository.findById(draftCase.getId())).thenReturn(Optional.of(draftCase));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> caseService.getCase(draftCase.getId()));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void updateCase_forbiddenForNonCreatorCommitteeMember() {
        UUID actorId = UUID.randomUUID();
        User actor = user(actorId, Role.COMMITTEE_MEMBER);
        User creator = user(UUID.randomUUID(), Role.COMMITTEE_MEMBER);
        CaseEntry submittedCase = caseEntry(UUID.randomUUID(), CaseStatus.SUBMITTED, creator);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null)
        );
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(caseRepository.findById(submittedCase.getId())).thenReturn(Optional.of(submittedCase));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> caseService.updateCase(
                        submittedCase.getId(),
                        new UpdateCaseRequest(
                                "Client",
                                BigDecimal.TEN,
                                "TERM_LOAN",
                                "12m",
                                "summary",
                                "risk",
                                "collateral",
                                null
                        )
                ));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(caseRepository, never()).save(submittedCase);
    }

    @Test
    void createCase_returnsFlushedTimestamps() {
        UUID actorId = UUID.randomUUID();
        User actor = user(actorId, Role.ADMIN);
        LocalDateTime createdAt = LocalDateTime.now();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null)
        );
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(caseRepository.saveAndFlush(any(CaseEntry.class))).thenAnswer(invocation -> {
            CaseEntry saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(createdAt);
            saved.setUpdatedAt(createdAt);
            return saved;
        });

        CaseResponse response = caseService.createCase(new CreateCaseRequest(
                "Client",
                BigDecimal.valueOf(1000),
                "TERM_LOAN",
                "12m",
                "summary",
                "risk",
                "collateral",
                LocalDateTime.now().plusDays(1)
        ));

        assertNotNull(response.createdAt());
        assertNotNull(response.updatedAt());
        verify(caseRepository).saveAndFlush(any(CaseEntry.class));
        verify(messagingTemplate).convertAndSend("/topic/cases", response);
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
