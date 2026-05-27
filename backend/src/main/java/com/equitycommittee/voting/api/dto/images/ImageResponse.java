package com.equitycommittee.voting.api.dto.images;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record ImageResponse(
        UUID id,
        UUID caseId,
        UUID uploadedById,
        String imageUrl,
        String caption,
        int sortOrder,
        LocalDateTime createdAt
) {
}
