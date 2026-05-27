package com.equitycommittee.voting.api.dto.voting;

import com.equitycommittee.voting.domain.enums.VoteChoice;
import jakarta.validation.constraints.NotNull;

public record VoteRequest(
    @NotNull VoteChoice voteChoice,
    String reason
) {}
