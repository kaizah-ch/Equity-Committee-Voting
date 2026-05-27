package com.equitycommittee.voting.api.dto.cases;

import com.equitycommittee.voting.domain.entity.CaseEntry;
import com.equitycommittee.voting.domain.enums.CaseStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CaseResponse(
    UUID id,
    String referenceNumber,
    String clientName,
    BigDecimal requestedAmount,
    String productType,
    String tenure,
    String summary,
    String riskNotes,
    String collateralSummary,
    CaseStatus status,
    LocalDateTime votingDeadline,
    String verdict,
    UUID createdById,
    String createdByName,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static CaseResponse from(CaseEntry c) {
        return new CaseResponse(
            c.getId(), c.getReferenceNumber(), c.getClientName(),
            c.getRequestedAmount(), c.getProductType(), c.getTenure(),
            c.getSummary(), c.getRiskNotes(), c.getCollateralSummary(),
            c.getStatus(), c.getVotingDeadline(), c.getVerdict(),
            c.getCreatedBy().getId(), c.getCreatedBy().getFullName(),
            c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
