package com.equitycommittee.voting.service;

import com.equitycommittee.voting.api.dto.voting.VoteRequest;
import com.equitycommittee.voting.api.dto.voting.VoteResponse;
import com.equitycommittee.voting.domain.entity.CaseEntry;
import com.equitycommittee.voting.domain.entity.User;
import com.equitycommittee.voting.domain.entity.Vote;
import com.equitycommittee.voting.domain.enums.CaseStatus;
import com.equitycommittee.voting.domain.enums.Role;
import com.equitycommittee.voting.domain.enums.VoteChoice;
import com.equitycommittee.voting.domain.repository.CaseRepository;
import com.equitycommittee.voting.domain.repository.UserRepository;
import com.equitycommittee.voting.domain.repository.VoteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VotingServiceAuthTest {

    @Mock
    private VoteRepository voteRepository;
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

    private VotingService votingService;

    @BeforeEach
    void setUp() {
        votingService = new VotingService(
                voteRepository,
                caseRepository,
                userRepository,
                auditService,
                notificationService,
                messagingTemplate
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void castVote_forbiddenForSecretary() {
        UUID actorId = UUID.randomUUID();
        User actor = user(actorId, Role.SECRETARY);
        CaseEntry caseEntry = caseEntry(UUID.randomUUID(), CaseStatus.VOTING_OPEN, user(UUID.randomUUID(), Role.COMMITTEE_MEMBER));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null)
        );
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(caseRepository.findById(caseEntry.getId())).thenReturn(Optional.of(caseEntry));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> votingService.castVote(caseEntry.getId(), new VoteRequest(VoteChoice.APPROVE, "ok")));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(voteRepository, never()).existsByCaseEntryIdAndVoterId(caseEntry.getId(), actorId);
    }

    @Test
    void getVotes_forbiddenForUnrelatedCommitteeMemberOnDraftCase() {
        UUID actorId = UUID.randomUUID();
        User actor = user(actorId, Role.COMMITTEE_MEMBER);
        User creator = user(UUID.randomUUID(), Role.COMMITTEE_MEMBER);
        CaseEntry caseEntry = caseEntry(UUID.randomUUID(), CaseStatus.DRAFT, creator);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null)
        );
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(caseRepository.findById(caseEntry.getId())).thenReturn(Optional.of(caseEntry));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> votingService.getVotes(caseEntry.getId()));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(voteRepository, never()).findByCaseEntryId(caseEntry.getId());
    }

    @Test
    void getVotes_allowedForSecretaryWhenCaseNotDraft() {
        UUID actorId = UUID.randomUUID();
        User actor = user(actorId, Role.SECRETARY);
        User creator = user(UUID.randomUUID(), Role.COMMITTEE_MEMBER);
        CaseEntry caseEntry = caseEntry(UUID.randomUUID(), CaseStatus.SUBMITTED, creator);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null)
        );
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(caseRepository.findById(caseEntry.getId())).thenReturn(Optional.of(caseEntry));
        when(voteRepository.findByCaseEntryId(caseEntry.getId())).thenReturn(List.of());

        List<VoteResponse> votes = votingService.getVotes(caseEntry.getId());

        assertTrue(votes.isEmpty());
        verify(voteRepository).findByCaseEntryId(caseEntry.getId());
    }

    @Test
    void castVote_returnsFlushedVotedAt() {
        UUID actorId = UUID.randomUUID();
        User actor = user(actorId, Role.COMMITTEE_MEMBER);
        CaseEntry caseEntry = caseEntry(UUID.randomUUID(), CaseStatus.VOTING_OPEN, user(UUID.randomUUID(), Role.ADMIN));
        caseEntry.setVotingDeadline(LocalDateTime.now().plusDays(1));
        LocalDateTime votedAt = LocalDateTime.now();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null)
        );
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(caseRepository.findById(caseEntry.getId())).thenReturn(Optional.of(caseEntry));
        when(voteRepository.existsByCaseEntryIdAndVoterId(caseEntry.getId(), actorId)).thenReturn(false);
        when(voteRepository.saveAndFlush(any(Vote.class))).thenAnswer(invocation -> {
            Vote saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setVotedAt(votedAt);
            return saved;
        });
        when(voteRepository.findByCaseEntryId(caseEntry.getId())).thenReturn(List.of());
        when(userRepository.countByRoleInAndActiveTrue(List.of(Role.COMMITTEE_MEMBER, Role.CHAIRPERSON))).thenReturn(2L);

        VoteResponse response = votingService.castVote(caseEntry.getId(), new VoteRequest(VoteChoice.APPROVE, "ok"));

        assertNotNull(response.votedAt());
        verify(voteRepository).saveAndFlush(any(Vote.class));
    }

    @Test
    void finalizeExpiredVotingCases_finalizesExpiredCasesUsingDeadlineTally() {
        User actor = user(UUID.randomUUID(), Role.ADMIN);
        User voterA = user(UUID.randomUUID(), Role.COMMITTEE_MEMBER);
        User voterB = user(UUID.randomUUID(), Role.CHAIRPERSON);
        CaseEntry caseEntry = caseEntry(UUID.randomUUID(), CaseStatus.VOTING_OPEN, user(UUID.randomUUID(), Role.ADMIN));
        LocalDateTime now = LocalDateTime.now();

        Vote approveVote = Vote.builder()
                .id(UUID.randomUUID())
                .caseEntry(caseEntry)
                .voter(voterA)
                .voteChoice(VoteChoice.APPROVE)
                .build();
        Vote rejectVote = Vote.builder()
                .id(UUID.randomUUID())
                .caseEntry(caseEntry)
                .voter(voterB)
                .voteChoice(VoteChoice.REJECT)
                .build();
        Vote secondApproveVote = Vote.builder()
                .id(UUID.randomUUID())
                .caseEntry(caseEntry)
                .voter(user(UUID.randomUUID(), Role.COMMITTEE_MEMBER))
                .voteChoice(VoteChoice.APPROVE)
                .build();

        when(caseRepository.findByStatusAndVotingDeadlineBefore(CaseStatus.VOTING_OPEN, now))
                .thenReturn(List.of(caseEntry));
        when(voteRepository.findByCaseEntryId(caseEntry.getId()))
                .thenReturn(List.of(approveVote, rejectVote, secondApproveVote));
        when(userRepository.countByRoleInAndActiveTrue(List.of(Role.COMMITTEE_MEMBER, Role.CHAIRPERSON)))
                .thenReturn(3L);

        int finalized = votingService.finalizeExpiredVotingCases(actor, now);

        assertEquals(1, finalized);
        assertEquals(CaseStatus.APPROVED, caseEntry.getStatus());
        assertEquals("APPROVED", caseEntry.getVerdict());
        verify(caseRepository).saveAndFlush(caseEntry);
        verify(notificationService).notifyFinalVerdict(caseEntry);
        verify(auditService).log(eq("CASE"), eq(caseEntry.getId()), eq("VERDICT_FINALIZED"), eq(actor), any());
        verify(messagingTemplate).convertAndSend(eq("/topic/cases/" + caseEntry.getId() + "/verdict"), any(Object.class));
    }

    @Test
    void finalizeExpiredVotingCases_skipsAlreadyFinalizedCasesFromRepositoryResult() {
        User actor = user(UUID.randomUUID(), Role.ADMIN);
        CaseEntry caseEntry = caseEntry(UUID.randomUUID(), CaseStatus.APPROVED, user(UUID.randomUUID(), Role.ADMIN));
        LocalDateTime now = LocalDateTime.now();

        when(caseRepository.findByStatusAndVotingDeadlineBefore(CaseStatus.VOTING_OPEN, now))
                .thenReturn(List.of(caseEntry));

        int finalized = votingService.finalizeExpiredVotingCases(actor, now);

        assertEquals(1, finalized);
        verify(voteRepository, never()).findByCaseEntryId(caseEntry.getId());
        verify(caseRepository, never()).saveAndFlush(any(CaseEntry.class));
        verify(notificationService, never()).notifyFinalVerdict(any());
        verify(auditService, never()).log(any(), any(), any(), any(), any());
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
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
