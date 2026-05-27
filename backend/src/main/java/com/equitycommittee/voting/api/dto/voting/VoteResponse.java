package com.equitycommittee.voting.api.dto.voting;

import com.equitycommittee.voting.domain.entity.Vote;
import com.equitycommittee.voting.domain.enums.VoteChoice;

import java.time.LocalDateTime;
import java.util.UUID;

public record VoteResponse(
    UUID id,
    UUID caseId,
    UUID voterId,
    String voterName,
    VoteChoice voteChoice,
    String reason,
    LocalDateTime votedAt
) {
    public static VoteResponse from(Vote v) {
        return new VoteResponse(
            v.getId(), v.getCaseEntry().getId(),
            v.getVoter().getId(), v.getVoter().getFullName(),
            v.getVoteChoice(), v.getReason(), v.getVotedAt()
        );
    }
}
