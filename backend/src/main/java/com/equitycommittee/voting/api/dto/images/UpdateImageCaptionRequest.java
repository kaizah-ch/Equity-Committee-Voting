package com.equitycommittee.voting.api.dto.images;

import jakarta.validation.constraints.Size;

public record UpdateImageCaptionRequest(
        @Size(max = 500, message = "Caption must be 500 characters or fewer")
        String caption
) {
}
