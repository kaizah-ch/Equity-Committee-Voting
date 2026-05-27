package com.equitycommittee.voting.api.controller;

import com.equitycommittee.voting.api.dto.notifications.DeviceTokenRequest;
import com.equitycommittee.voting.service.DeviceTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/me/device-token")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    @PutMapping
    public ResponseEntity<Void> register(@Valid @RequestBody DeviceTokenRequest request) {
        deviceTokenService.register(currentUserId(), request.token(), request.platform());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> unregister(@Valid @RequestBody DeviceTokenRequest request) {
        deviceTokenService.unregister(currentUserId(), request.token());
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
