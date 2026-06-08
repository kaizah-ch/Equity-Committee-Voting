package com.equitycommittee.voting.service;

import com.equitycommittee.voting.api.dto.users.CreateUserRequest;
import com.equitycommittee.voting.api.dto.users.ResetPasswordRequest;
import com.equitycommittee.voting.api.dto.users.UpdateUserRequest;
import com.equitycommittee.voting.api.dto.users.UserResponse;
import com.equitycommittee.voting.domain.entity.User;
import com.equitycommittee.voting.domain.enums.Role;
import com.equitycommittee.voting.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(Pageable pageable) {
        requireAdmin();
        return userRepository.findAll(pageable).map(UserResponse::from);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        User actor = requireAdmin();
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already in use");
        }

        User user = User.builder()
                .email(email)
                .fullName(request.fullName().trim())
                .role(request.role())
                .password(passwordEncoder.encode(request.password()))
                .active(true)
                .build();
        user = userRepository.saveAndFlush(user);

        auditService.log("USER", user.getId(), "USER_CREATED", actor, Map.of(
                "email", user.getEmail(),
                "role", user.getRole().name()
        ));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {
        User actor = requireAdmin();
        User user = findUser(userId);
        String email = normalizeEmail(request.email());

        userRepository.findByEmailIgnoreCase(email)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already in use");
                });

        boolean activeChanged = user.isActive() != request.active();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        user.setActive(request.active());
        user = userRepository.saveAndFlush(user);

        auditService.log("USER", user.getId(), "USER_UPDATED", actor, Map.of(
                "email", user.getEmail(),
                "role", user.getRole().name(),
                "active", String.valueOf(user.isActive())
        ));
        if (activeChanged) {
            auditService.log("USER", user.getId(), user.isActive() ? "USER_REACTIVATED" : "USER_DEACTIVATED", actor,
                    Map.of("email", user.getEmail()));
        }
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse deactivateUser(UUID userId) {
        User actor = requireAdmin();
        if (actor.getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot deactivate your own account");
        }
        User user = findUser(userId);
        user.setActive(false);
        user = userRepository.saveAndFlush(user);
        auditService.log("USER", user.getId(), "USER_DEACTIVATED", actor, Map.of("email", user.getEmail()));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse reactivateUser(UUID userId) {
        User actor = requireAdmin();
        User user = findUser(userId);
        user.setActive(true);
        user = userRepository.saveAndFlush(user);
        auditService.log("USER", user.getId(), "USER_REACTIVATED", actor, Map.of("email", user.getEmail()));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse resetPassword(UUID userId, ResetPasswordRequest request) {
        User actor = requireAdmin();
        User user = findUser(userId);
        user.setPassword(passwordEncoder.encode(request.password()));
        user = userRepository.saveAndFlush(user);
        auditService.log("USER", user.getId(), "PASSWORD_RESET", actor, Map.of("email", user.getEmail()));
        return UserResponse.from(user);
    }

    private User requireAdmin() {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        User actor = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (actor.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can manage users");
        }
        if (!actor.isActive()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account is inactive");
        }
        return actor;
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
