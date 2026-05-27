package com.equitycommittee.voting.api.controller;

import com.equitycommittee.voting.api.dto.notifications.NotificationResponse;
import com.equitycommittee.voting.domain.entity.Notification;
import com.equitycommittee.voting.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @PageableDefault(size = 20) Pageable pageable) {
        UUID userId = currentUserId();
        Page<Notification> notifications = notificationService.getUserNotifications(userId, pageable);
        return ResponseEntity.ok(notifications.map(NotificationResponse::from));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id) {
        notificationService.markRead(id, currentUserId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        notificationService.markAllRead(currentUserId());
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
