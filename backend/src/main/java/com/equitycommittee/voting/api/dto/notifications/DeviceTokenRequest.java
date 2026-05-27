package com.equitycommittee.voting.api.dto.notifications;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceTokenRequest(
        @NotBlank @Size(max = 4096) String token,
        @Size(max = 50) String platform
) {
}
