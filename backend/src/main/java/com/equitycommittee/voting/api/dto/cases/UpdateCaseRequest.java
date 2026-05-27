package com.equitycommittee.voting.api.dto.cases;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UpdateCaseRequest(
        @NotBlank String clientName,
        @NotNull @Positive BigDecimal requestedAmount,
        @NotBlank String productType,
        String tenure,
        String summary,
        String riskNotes,
        String collateralSummary,
        LocalDateTime votingDeadline
) {}

