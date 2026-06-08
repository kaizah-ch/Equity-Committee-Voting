package com.equitycommittee.voting.api.dto.users;

import com.equitycommittee.voting.domain.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 2, max = 150) String fullName,
        @NotNull Role role,
        @NotNull Boolean active
) {}
