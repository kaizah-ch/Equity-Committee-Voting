package com.equitycommittee.voting.api.dto.cases;

import com.equitycommittee.voting.domain.enums.CaseStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateCaseStatusRequest(@NotNull CaseStatus status) {}
