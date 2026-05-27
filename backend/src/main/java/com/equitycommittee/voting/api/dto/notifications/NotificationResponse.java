package com.equitycommittee.voting.api.dto.notifications;

import com.equitycommittee.voting.domain.entity.Notification;
import com.equitycommittee.voting.domain.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID userId,
        NotificationType type,
        String title,
        String body,
        UUID caseId,
        boolean isRead,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getUser().getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getCaseEntry() == null ? null : notification.getCaseEntry().getId(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
