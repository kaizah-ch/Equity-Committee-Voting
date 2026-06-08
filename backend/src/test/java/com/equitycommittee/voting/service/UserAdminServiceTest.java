package com.equitycommittee.voting.service;

import com.equitycommittee.voting.api.dto.users.CreateUserRequest;
import com.equitycommittee.voting.api.dto.users.ResetPasswordRequest;
import com.equitycommittee.voting.api.dto.users.UpdateUserRequest;
import com.equitycommittee.voting.api.dto.users.UserResponse;
import com.equitycommittee.voting.domain.entity.User;
import com.equitycommittee.voting.domain.enums.Role;
import com.equitycommittee.voting.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService auditService;

    private UserAdminService userAdminService;

    @BeforeEach
    void setUp() {
        userAdminService = new UserAdminService(userRepository, passwordEncoder, auditService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listUsers_forbiddenForNonAdmin() {
        UUID actorId = UUID.randomUUID();
        authenticate(actorId);
        when(userRepository.findById(actorId)).thenReturn(Optional.of(user(actorId, Role.COMMITTEE_MEMBER)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userAdminService.listUsers(PageRequest.of(0, 20)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(userRepository, never()).findAll(any(PageRequest.class));
    }

    @Test
    void createUser_normalizesEmailHashesPasswordAndAudits() {
        UUID actorId = UUID.randomUUID();
        User actor = user(actorId, Role.ADMIN);
        authenticate(actorId);

        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(userRepository.existsByEmailIgnoreCase("new.user@equity.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        UserResponse response = userAdminService.createUser(new CreateUserRequest(
                " New.User@Equity.com ",
                "New User",
                Role.SECRETARY,
                "Password123"
        ));

        assertEquals("new.user@equity.com", response.email());
        assertEquals(Role.SECRETARY, response.role());
        verify(passwordEncoder).encode("Password123");
        verify(auditService).log(eq("USER"), eq(response.id()), eq("USER_CREATED"), eq(actor), any());
    }

    @Test
    void createUser_rejectsDuplicateEmail() {
        UUID actorId = UUID.randomUUID();
        authenticate(actorId);
        when(userRepository.findById(actorId)).thenReturn(Optional.of(user(actorId, Role.ADMIN)));
        when(userRepository.existsByEmailIgnoreCase("existing@equity.com")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userAdminService.createUser(new CreateUserRequest(
                        "existing@equity.com",
                        "Existing User",
                        Role.COMMITTEE_MEMBER,
                        "Password123"
                )));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void deactivateUser_rejectsSelfDeactivation() {
        UUID actorId = UUID.randomUUID();
        authenticate(actorId);
        when(userRepository.findById(actorId)).thenReturn(Optional.of(user(actorId, Role.ADMIN)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userAdminService.deactivateUser(actorId));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void deactivateUser_setsInactiveAndAudits() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        User actor = user(actorId, Role.ADMIN);
        User target = user(targetId, Role.CHAIRPERSON);
        authenticate(actorId);

        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(userRepository.saveAndFlush(target)).thenReturn(target);

        UserResponse response = userAdminService.deactivateUser(targetId);

        assertFalse(response.active());
        verify(auditService).log(eq("USER"), eq(targetId), eq("USER_DEACTIVATED"), eq(actor), any());
    }

    @Test
    void resetPassword_hashesPasswordAndAudits() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        User actor = user(actorId, Role.ADMIN);
        User target = user(targetId, Role.COMMITTEE_MEMBER);
        authenticate(actorId);

        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(passwordEncoder.encode("NewPassword123")).thenReturn("new-hash");
        when(userRepository.saveAndFlush(target)).thenReturn(target);

        userAdminService.resetPassword(targetId, new ResetPasswordRequest("NewPassword123"));

        assertEquals("new-hash", target.getPassword());
        verify(auditService).log(eq("USER"), eq(targetId), eq("PASSWORD_RESET"), eq(actor), any());
    }

    @Test
    void updateUser_rejectsEmailUsedByAnotherUser() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        User actor = user(actorId, Role.ADMIN);
        User target = user(targetId, Role.SECRETARY);
        User other = user(UUID.randomUUID(), Role.COMMITTEE_MEMBER);
        authenticate(actorId);

        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(userRepository.findByEmailIgnoreCase("other@equity.com")).thenReturn(Optional.of(other));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userAdminService.updateUser(targetId, new UpdateUserRequest(
                        "other@equity.com",
                        "Updated User",
                        Role.CHAIRPERSON,
                        true
                )));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(userRepository, never()).saveAndFlush(target);
    }

    @Test
    void listUsers_returnsPageForAdmin() {
        UUID actorId = UUID.randomUUID();
        authenticate(actorId);
        User actor = user(actorId, Role.ADMIN);
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(userRepository.findAll(PageRequest.of(0, 20))).thenReturn(new PageImpl<>(List.of(actor)));

        var page = userAdminService.listUsers(PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        assertEquals(actor.getEmail(), page.getContent().getFirst().email());
    }

    private void authenticate(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId.toString(), null)
        );
    }

    private User user(UUID id, Role role) {
        return User.builder()
                .id(id)
                .email(role.name().toLowerCase() + "@equity.com")
                .password("hash")
                .fullName("User")
                .role(role)
                .active(true)
                .build();
    }
}
