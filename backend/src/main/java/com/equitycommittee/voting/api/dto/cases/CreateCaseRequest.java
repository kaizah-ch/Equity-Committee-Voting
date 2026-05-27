package com.equitycommittee.voting.api.dto.cases;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateCaseRequest(
    @NotBlank String clientName,
    @NotNull @Positive BigDecimal requestedAmount,
    @NotBlank String productType,
    String tenure,
    String summary,
    String riskNotes,
    String collateralSummary,
    LocalDateTime votingDeadline
) {}
