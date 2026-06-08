package com.equitycommittee.voting.api.controller;

import com.equitycommittee.voting.api.dto.users.CreateUserRequest;
import com.equitycommittee.voting.api.dto.users.ResetPasswordRequest;
import com.equitycommittee.voting.api.dto.users.UpdateUserRequest;
import com.equitycommittee.voting.api.dto.users.UserResponse;
import com.equitycommittee.voting.service.UserAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    @GetMapping
    public ResponseEntity<Page<UserResponse>> listUsers(@PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(userAdminService.listUsers(pageable));
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userAdminService.createUser(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userAdminService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<UserResponse> deactivateUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userAdminService.deactivateUser(id));
    }

    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<UserResponse> reactivateUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userAdminService.reactivateUser(id));
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<UserResponse> resetPassword(
            @PathVariable UUID id,
            @Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(userAdminService.resetPassword(id, request));
    }
}
