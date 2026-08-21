package com.equitycommittee.voting.service;

import com.equitycommittee.voting.api.dto.voting.VoteRequest;
import com.equitycommittee.voting.domain.entity.CaseEntry;
import com.equitycommittee.voting.domain.entity.User;
import com.equitycommittee.voting.domain.entity.Vote;
import com.equitycommittee.voting.domain.enums.CaseStatus;
import com.equitycommittee.voting.domain.enums.Role;
import com.equitycommittee.voting.domain.enums.VoteChoice;
import com.equitycommittee.voting.domain.repository.AuditLogRepository;
import com.equitycommittee.voting.domain.repository.CaseImageRepository;
import com.equitycommittee.voting.domain.repository.CaseRepository;
import com.equitycommittee.voting.domain.repository.NotificationRepository;
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
import software.amazon.awssdk.services.s3.S3Client;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionMatrixRegressionTest {

    private static final Set<CaseStatus> COMMITTEE_VISIBLE_STATUSES = EnumSet.of(
            CaseStatus.UNDER_REVIEW,
            CaseStatus.VOTING_OPEN,
            CaseStatus.APPROVED,
            CaseStatus.REJECTED,
            CaseStatus.DEFERRED,
            CaseStatus.CLOSED
    );

    @Mock
    private CaseRepository caseRepository;
    @Mock
    private CaseImageRepository imageRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private S3Client s3Client;
    @Mock
    private VoteRepository voteRepository;

    private CaseService caseService;
    private VotingService votingService;

    @BeforeEach
    void setUp() {
        caseService = new CaseService(
                caseRepository,
                imageRepository,
                notificationRepository,
                auditLogRepository,
                userRepository,
                auditService,
                notificationService,
                messagingTemplate,
                s3Client
        );
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
    void caseVisibility_unrelatedUsersFollowRoleAndStatusMatrix() {
        for (Role role : Role.values()) {
            for (CaseStatus status : CaseStatus.values()) {
                reset(userRepository, caseRepository);
                UUID actorId = UUID.randomUUID();
                User actor = user(actorId, role);
                CaseEntry caseEntry = caseEntry(status, user(UUID.randomUUID(), Role.CREDIT_OFFICER));
                authenticate(actorId);
                when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
                when(caseRepository.findById(caseEntry.getId())).thenReturn(Optional.of(caseEntry));

                if (canUnrelatedUserView(role, status)) {
                    assertDoesNotThrow(() -> caseService.getCase(caseEntry.getId()),
                            role + " should view " + status);
                } else {
                    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                            () -> caseService.getCase(caseEntry.getId()),
                            role + " should not view " + status);
                    assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
                }
            }
        }
    }

    @Test
    void caseVisibility_creatorCanViewOwnCaseAcrossAllStatuses() {
        for (Role role : Role.values()) {
            for (CaseStatus status : CaseStatus.values()) {
                reset(userRepository, caseRepository);
                UUID actorId = UUID.randomUUID();
                User actor = user(actorId, role);
                CaseEntry caseEntry = caseEntry(status, actor);
                authenticate(actorId);
                when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
                when(caseRepository.findById(caseEntry.getId())).thenReturn(Optional.of(caseEntry));

                assertDoesNotThrow(() -> caseService.getCase(caseEntry.getId()),
                        "creator " + role + " should view own " + status + " case");
            }
        }
    }

    @Test
    void votingOpen_allRolesFollowVotingEligibilityMatrix() {
        for (Role role : Role.values()) {
            reset(userRepository, caseRepository, voteRepository);
            UUID actorId = UUID.randomUUID();
            User actor = user(actorId, role);
            CaseEntry caseEntry = caseEntry(CaseStatus.VOTING_OPEN, user(UUID.randomUUID(), Role.MANAGER));
            caseEntry.setVotingDeadline(LocalDateTime.now().plusDays(1));
            authenticate(actorId);
            when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
            when(caseRepository.findById(caseEntry.getId())).thenReturn(Optional.of(caseEntry));

            if (role == Role.COMMITTEE_MEMBER || role == Role.CHAIRPERSON) {
                when(voteRepository.existsByCaseEntryIdAndVoterId(caseEntry.getId(), actorId)).thenReturn(false);
                when(voteRepository.saveAndFlush(any(Vote.class))).thenAnswer(invocation -> {
                    Vote vote = invocation.getArgument(0);
                    vote.setId(UUID.randomUUID());
                    vote.setVotedAt(LocalDateTime.now());
                    return vote;
                });
                when(voteRepository.findByCaseEntryId(caseEntry.getId())).thenReturn(List.of());
                when(userRepository.countByRoleInAndActiveTrue(List.of(Role.COMMITTEE_MEMBER, Role.CHAIRPERSON)))
                        .thenReturn(2L);

                assertDoesNotThrow(() -> votingService.castVote(
                        caseEntry.getId(),
                        new VoteRequest(VoteChoice.APPROVE, "ok")
                ), role + " should vote while voting is open");
            } else {
                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                        () -> votingService.castVote(
                                caseEntry.getId(),
                                new VoteRequest(VoteChoice.APPROVE, "ok")
                        ),
                        role + " should not vote");
                assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
            }
        }
    }

    private static boolean canUnrelatedUserView(Role role, CaseStatus status) {
        if (role == Role.ADMIN) {
            return true;
        }
        if (role == Role.MANAGER) {
            return status != CaseStatus.DRAFT;
        }
        if (role == Role.COMMITTEE_MEMBER || role == Role.SECRETARY || role == Role.CHAIRPERSON) {
            return COMMITTEE_VISIBLE_STATUSES.contains(status);
        }
        return false;
    }

    private static void authenticate(UUID actorId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null)
        );
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

    private static CaseEntry caseEntry(CaseStatus status, User createdBy) {
        UUID id = UUID.randomUUID();
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
