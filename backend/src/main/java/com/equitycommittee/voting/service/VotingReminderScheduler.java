package com.equitycommittee.voting.service;

import com.equitycommittee.voting.domain.entity.CaseEntry;
import com.equitycommittee.voting.domain.entity.User;
import com.equitycommittee.voting.domain.enums.CaseStatus;
import com.equitycommittee.voting.domain.enums.NotificationType;
import com.equitycommittee.voting.domain.enums.Role;
import com.equitycommittee.voting.domain.repository.CaseRepository;
import com.equitycommittee.voting.domain.repository.UserRepository;
import com.equitycommittee.voting.domain.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class VotingReminderScheduler {

    private static final List<Role> ELIGIBLE_VOTER_ROLES = List.of(Role.COMMITTEE_MEMBER, Role.CHAIRPERSON);

    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final VoteRepository voteRepository;
    private final NotificationService notificationService;
    private final VotingService votingService;

    @Value("${app.notifications.vote-reminder-cooldown-minutes:360}")
    private long voteReminderCooldownMinutes;

    @Value("${app.notifications.deadline-alert-cooldown-minutes:720}")
    private long deadlineAlertCooldownMinutes;

    @Value("${app.notifications.deadline-approaching-hours:24}")
    private long deadlineApproachingHours;

    @Value("${app.audit.system-actor-email:admin@equity.com}")
    private String systemActorEmail;

    @Scheduled(fixedDelayString = "${app.notifications.scheduler-interval-ms:300000}")
    @Transactional
    public void dispatchVotingNotifications() {
        LocalDateTime now = LocalDateTime.now();
        User systemActor = userRepository.findByEmail(systemActorEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "System audit actor not found: " + systemActorEmail));
        votingService.finalizeExpiredVotingCases(systemActor, now);

        List<User> eligibleUsers = userRepository.findByRoleInAndActiveTrue(ELIGIBLE_VOTER_ROLES);
        if (eligibleUsers.isEmpty()) {
            return;
        }

        List<CaseEntry> openCases = caseRepository.findByStatus(CaseStatus.VOTING_OPEN);
        for (CaseEntry caseEntry : openCases) {
            LocalDateTime deadline = caseEntry.getVotingDeadline();
            if (deadline == null || !deadline.isAfter(now)) {
                continue;
            }

            sendVoteReminders(caseEntry, eligibleUsers, now);
            if (deadline.isBefore(now.plusHours(deadlineApproachingHours))) {
                sendDeadlineApproachingAlerts(caseEntry, eligibleUsers, now);
            }
        }
    }

    private void sendVoteReminders(CaseEntry caseEntry, List<User> eligibleUsers, LocalDateTime now) {
        LocalDateTime cutoff = now.minusMinutes(voteReminderCooldownMinutes);
        for (User user : eligibleUsers) {
            if (voteRepository.existsByCaseEntryIdAndVoterId(caseEntry.getId(), user.getId())) {
                continue;
            }
            notificationService.notifyUserIfNotRecentlySent(
                    user,
                    NotificationType.VOTE_REMINDER,
                    "Vote Reminder: " + caseEntry.getReferenceNumber(),
                    "Please cast your vote for case: " + caseEntry.getClientName(),
                    caseEntry,
                    cutoff
            );
        }
    }

    private void sendDeadlineApproachingAlerts(CaseEntry caseEntry, List<User> eligibleUsers, LocalDateTime now) {
        LocalDateTime cutoff = now.minusMinutes(deadlineAlertCooldownMinutes);
        for (User user : eligibleUsers) {
            notificationService.notifyUserIfNotRecentlySent(
                    user,
                    NotificationType.VOTING_DEADLINE_APPROACHING,
                    "Deadline Approaching: " + caseEntry.getReferenceNumber(),
                    "Voting deadline is approaching for case: " + caseEntry.getClientName(),
                    caseEntry,
                    cutoff
            );
        }
    }
}
