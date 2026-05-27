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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VotingService {

    private final VoteRepository voteRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public VoteResponse castVote(UUID caseId, VoteRequest req) {
        User voter = currentUser();
        CaseEntry caseEntry = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));
        assertCanCastVote(voter, caseEntry);

        if (caseEntry.getStatus() != CaseStatus.VOTING_OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Voting is not open for this case");
        }
        if (caseEntry.getVotingDeadline() != null && LocalDateTime.now().isAfter(caseEntry.getVotingDeadline())) {
            finalizeDeadlineCase(caseEntry, voter);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Voting deadline has passed for this case");
        }
        if (voteRepository.existsByCaseEntryIdAndVoterId(caseId, voter.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already voted on this case");
        }

        Vote vote = Vote.builder()
                .caseEntry(caseEntry)
                .voter(voter)
                .voteChoice(req.voteChoice())
                .reason(req.reason())
                .build();

        vote = voteRepository.saveAndFlush(vote);
        auditService.log("VOTE", vote.getId(), "CAST", voter,
                Map.of("caseId", caseId.toString(), "choice", req.voteChoice().name()));

        VoteResponse response = VoteResponse.from(vote);
        messagingTemplate.convertAndSend("/topic/cases/" + caseId + "/votes", response);
        finalizeByMajority(caseEntry, voter);
        return response;
    }

    @Transactional(readOnly = true)
    public List<VoteResponse> getVotes(UUID caseId) {
        User actor = currentUser();
        CaseEntry caseEntry = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));
        assertCanViewVotes(actor, caseEntry);

        return voteRepository.findByCaseEntryId(caseId).stream()
                .map(VoteResponse::from)
                .toList();
    }

    @Transactional
    public int finalizeExpiredVotingCases(User actor, LocalDateTime now) {
        List<CaseEntry> expiredCases = caseRepository.findByStatusAndVotingDeadlineBefore(CaseStatus.VOTING_OPEN, now);
        for (CaseEntry caseEntry : expiredCases) {
            finalizeDeadlineCase(caseEntry, actor);
        }
        return expiredCases.size();
    }

    private User currentUser() {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private void finalizeByMajority(CaseEntry caseEntry, User actor) {
        if (caseEntry.getStatus() != CaseStatus.VOTING_OPEN) {
            return;
        }

        List<Vote> votes = voteRepository.findByCaseEntryId(caseEntry.getId());
        long eligibleVoters = userRepository.countByRoleInAndActiveTrue(
                List.of(Role.COMMITTEE_MEMBER, Role.CHAIRPERSON));
        if (eligibleVoters <= 0) {
            return;
        }

        Map<VoteChoice, Long> tally = tally(votes);
        long approve = tally.getOrDefault(VoteChoice.APPROVE, 0L);
        long reject = tally.getOrDefault(VoteChoice.REJECT, 0L);
        long defer = tally.getOrDefault(VoteChoice.DEFER, 0L);
        long totalVotes = votes.size();
        long threshold = (eligibleVoters / 2) + 1;

        if (approve >= threshold) {
            finalizeCase(caseEntry, CaseStatus.APPROVED, actor, eligibleVoters, totalVotes, "majority");
            return;
        }
        if (reject >= threshold) {
            finalizeCase(caseEntry, CaseStatus.REJECTED, actor, eligibleVoters, totalVotes, "majority");
            return;
        }
        if (defer >= threshold) {
            finalizeCase(caseEntry, CaseStatus.DEFERRED, actor, eligibleVoters, totalVotes, "majority");
            return;
        }

        // Everyone eligible has voted and still no majority -> defer.
        if (totalVotes >= eligibleVoters) {
            finalizeCase(caseEntry, CaseStatus.DEFERRED, actor, eligibleVoters, totalVotes, "no-majority");
        }
    }

    private void finalizeDeadlineCase(CaseEntry caseEntry, User actor) {
        if (caseEntry.getStatus() != CaseStatus.VOTING_OPEN) {
            return;
        }

        List<Vote> votes = voteRepository.findByCaseEntryId(caseEntry.getId());
        long eligibleVoters = userRepository.countByRoleInAndActiveTrue(
                List.of(Role.COMMITTEE_MEMBER, Role.CHAIRPERSON));
        long totalVotes = votes.size();

        Map<VoteChoice, Long> tally = tally(votes);
        long approve = tally.getOrDefault(VoteChoice.APPROVE, 0L);
        long reject = tally.getOrDefault(VoteChoice.REJECT, 0L);
        long defer = tally.getOrDefault(VoteChoice.DEFER, 0L);

        CaseStatus finalStatus = CaseStatus.DEFERRED;
        if (approve > reject && approve > defer) {
            finalStatus = CaseStatus.APPROVED;
        } else if (reject > approve && reject > defer) {
            finalStatus = CaseStatus.REJECTED;
        } else if (defer > approve && defer > reject) {
            finalStatus = CaseStatus.DEFERRED;
        }

        finalizeCase(caseEntry, finalStatus, actor, eligibleVoters, totalVotes, "deadline");
    }

    private Map<VoteChoice, Long> tally(List<Vote> votes) {
        Map<VoteChoice, Long> tally = new EnumMap<>(VoteChoice.class);
        for (Vote v : votes) {
            tally.put(v.getVoteChoice(), tally.getOrDefault(v.getVoteChoice(), 0L) + 1);
        }
        return tally;
    }

    private void finalizeCase(
            CaseEntry caseEntry,
            CaseStatus finalStatus,
            User actor,
            long eligibleVoters,
            long totalVotes,
            String reason
    ) {
        if (caseEntry.getStatus() != CaseStatus.VOTING_OPEN) {
            return;
        }

        caseEntry.setStatus(finalStatus);
        caseEntry.setVerdict(finalStatus.name());
        caseRepository.saveAndFlush(caseEntry);

        auditService.log("CASE", caseEntry.getId(), "VERDICT_FINALIZED", actor, Map.of(
                "verdict", finalStatus.name(),
                "reason", reason,
                "eligibleVoters", String.valueOf(eligibleVoters),
                "totalVotes", String.valueOf(totalVotes)
        ));

        notificationService.notifyFinalVerdict(caseEntry);
        Object verdictPayload = Map.of(
                "caseId", caseEntry.getId().toString(),
                "status", finalStatus.name(),
                "verdict", finalStatus.name()
        );
        messagingTemplate.convertAndSend("/topic/cases/" + caseEntry.getId() + "/verdict", verdictPayload);
    }

    private void assertCanCastVote(User actor, CaseEntry caseEntry) {
        if (actor.getRole() != Role.COMMITTEE_MEMBER && actor.getRole() != Role.CHAIRPERSON) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only committee members and chairperson can vote");
        }

        if (caseEntry.getStatus() == CaseStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to vote on draft case");
        }
    }

    private void assertCanViewVotes(User actor, CaseEntry caseEntry) {
        boolean isCaseCreator = caseEntry.getCreatedBy() != null
                && caseEntry.getCreatedBy().getId() != null
                && caseEntry.getCreatedBy().getId().equals(actor.getId());
        boolean isPrivilegedRole = actor.getRole() == Role.ADMIN || actor.getRole() == Role.CHAIRPERSON;
        if (isCaseCreator || isPrivilegedRole) {
            return;
        }

        if (caseEntry.getStatus() == CaseStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to access votes for draft case");
        }

        boolean isCommitteeRole = actor.getRole() == Role.COMMITTEE_MEMBER || actor.getRole() == Role.SECRETARY;
        if (!isCommitteeRole) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to access case votes");
        }
    }
}
