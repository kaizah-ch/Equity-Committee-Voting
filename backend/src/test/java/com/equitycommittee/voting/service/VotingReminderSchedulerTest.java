package com.equitycommittee.voting.service;

import com.equitycommittee.voting.domain.entity.CaseEntry;
import com.equitycommittee.voting.domain.entity.User;
import com.equitycommittee.voting.domain.enums.CaseStatus;
import com.equitycommittee.voting.domain.enums.NotificationType;
import com.equitycommittee.voting.domain.enums.Role;
import com.equitycommittee.voting.domain.repository.CaseRepository;
import com.equitycommittee.voting.domain.repository.UserRepository;
import com.equitycommittee.voting.domain.repository.VoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VotingReminderSchedulerTest {

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VoteRepository voteRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private VotingService votingService;

    private VotingReminderScheduler scheduler;
    private User systemActor;

    @BeforeEach
    void setUp() {
        scheduler = new VotingReminderScheduler(caseRepository, userRepository, voteRepository, notificationService, votingService);
        ReflectionTestUtils.setField(scheduler, "voteReminderCooldownMinutes", 360L);
        ReflectionTestUtils.setField(scheduler, "deadlineAlertCooldownMinutes", 720L);
        ReflectionTestUtils.setField(scheduler, "deadlineApproachingHours", 24L);
        ReflectionTestUtils.setField(scheduler, "systemActorEmail", "admin@equity.com");
        systemActor = user("admin@equity.com", Role.ADMIN);
    }

    @Test
    void dispatchVotingNotifications_whenNoEligibleUsers_doesNothing() {
        when(userRepository.findByEmail("admin@equity.com")).thenReturn(Optional.of(systemActor));
        when(userRepository.findByRoleInAndActiveTrue(any())).thenReturn(List.of());

        scheduler.dispatchVotingNotifications();

        verify(votingService).finalizeExpiredVotingCases(eq(systemActor), any());
        verify(caseRepository, never()).findByStatus(any());
        verify(notificationService, never()).notifyUserIfNotRecentlySent(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void dispatchVotingNotifications_sendsReminderToNonVotersAndDeadlineAlertsWithinWindow() {
        User voterA = user("a@equity.com", Role.COMMITTEE_MEMBER);
        User voterB = user("b@equity.com", Role.CHAIRPERSON);
        CaseEntry openCase = votingCase(LocalDateTime.now().plusHours(2));

        when(userRepository.findByEmail("admin@equity.com")).thenReturn(Optional.of(systemActor));
        when(userRepository.findByRoleInAndActiveTrue(any())).thenReturn(List.of(voterA, voterB));
        when(caseRepository.findByStatus(CaseStatus.VOTING_OPEN)).thenReturn(List.of(openCase));
        when(voteRepository.existsByCaseEntryIdAndVoterId(openCase.getId(), voterA.getId())).thenReturn(true);
        when(voteRepository.existsByCaseEntryIdAndVoterId(openCase.getId(), voterB.getId())).thenReturn(false);

        LocalDateTime before = LocalDateTime.now();
        scheduler.dispatchVotingNotifications();
        LocalDateTime after = LocalDateTime.now();

        verify(notificationService, times(1)).notifyUserIfNotRecentlySent(
                eq(voterB),
                eq(NotificationType.VOTE_REMINDER),
                any(),
                any(),
                eq(openCase),
                any()
        );
        verify(notificationService, times(2)).notifyUserIfNotRecentlySent(
                any(),
                eq(NotificationType.VOTING_DEADLINE_APPROACHING),
                any(),
                any(),
                eq(openCase),
                any()
        );

        ArgumentCaptor<LocalDateTime> reminderCutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(notificationService).notifyUserIfNotRecentlySent(
                eq(voterB),
                eq(NotificationType.VOTE_REMINDER),
                any(),
                any(),
                eq(openCase),
                reminderCutoffCaptor.capture()
        );
        LocalDateTime reminderCutoff = reminderCutoffCaptor.getValue();
        assertTrue(!reminderCutoff.isBefore(before.minusMinutes(360)) &&
                !reminderCutoff.isAfter(after.minusMinutes(360)));

        ArgumentCaptor<LocalDateTime> deadlineCutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(notificationService, times(2)).notifyUserIfNotRecentlySent(
                any(),
                eq(NotificationType.VOTING_DEADLINE_APPROACHING),
                any(),
                any(),
                eq(openCase),
                deadlineCutoffCaptor.capture()
        );
        for (LocalDateTime cutoff : deadlineCutoffCaptor.getAllValues()) {
            assertTrue(!cutoff.isBefore(before.minusMinutes(720)) &&
                    !cutoff.isAfter(after.minusMinutes(720)));
        }
    }

    @Test
    void dispatchVotingNotifications_whenDeadlineOutsideWindow_sendsOnlyVoteReminder() {
        User voter = user("member@equity.com", Role.COMMITTEE_MEMBER);
        CaseEntry openCase = votingCase(LocalDateTime.now().plusHours(30));

        when(userRepository.findByEmail("admin@equity.com")).thenReturn(Optional.of(systemActor));
        when(userRepository.findByRoleInAndActiveTrue(any())).thenReturn(List.of(voter));
        when(caseRepository.findByStatus(CaseStatus.VOTING_OPEN)).thenReturn(List.of(openCase));
        when(voteRepository.existsByCaseEntryIdAndVoterId(openCase.getId(), voter.getId())).thenReturn(false);

        scheduler.dispatchVotingNotifications();

        verify(notificationService, times(1)).notifyUserIfNotRecentlySent(
                eq(voter),
                eq(NotificationType.VOTE_REMINDER),
                any(),
                any(),
                eq(openCase),
                any()
        );
        verify(notificationService, never()).notifyUserIfNotRecentlySent(
                any(),
                eq(NotificationType.VOTING_DEADLINE_APPROACHING),
                any(),
                any(),
                any(),
                any()
        );
    }

    private User user(String email, Role role) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .password("secret")
                .fullName("Test User")
                .role(role)
                .active(true)
                .build();
    }

    private CaseEntry votingCase(LocalDateTime deadline) {
        return CaseEntry.builder()
                .id(UUID.randomUUID())
                .referenceNumber("ECV-TEST")
                .clientName("Client A")
                .status(CaseStatus.VOTING_OPEN)
                .votingDeadline(deadline)
                .build();
    }
}
