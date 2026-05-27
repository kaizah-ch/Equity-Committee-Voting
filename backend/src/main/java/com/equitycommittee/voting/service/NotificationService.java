package com.equitycommittee.voting.service;

import com.equitycommittee.voting.domain.entity.CaseEntry;
import com.equitycommittee.voting.domain.entity.Notification;
import com.equitycommittee.voting.domain.entity.User;
import com.equitycommittee.voting.domain.enums.NotificationType;
import com.equitycommittee.voting.domain.repository.NotificationRepository;
import com.equitycommittee.voting.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PushNotificationService pushNotificationService;

    @Async
    public void notifyCaseCreated(CaseEntry caseEntry) {
        List<User> allUsers = userRepository.findAll();
        allUsers.forEach(user -> createNotification(user, NotificationType.CASE_CREATED,
                "New Case: " + caseEntry.getReferenceNumber(),
                "A new case has been submitted: " + caseEntry.getClientName(),
                caseEntry));
    }

    @Async
    public void notifyVotingOpened(CaseEntry caseEntry) {
        List<User> allUsers = userRepository.findAll();
        allUsers.forEach(user -> createNotification(user, NotificationType.VOTING_OPENED,
                "Voting Open: " + caseEntry.getReferenceNumber(),
                "Voting is now open for case: " + caseEntry.getClientName(),
                caseEntry));
    }

    @Async
    public void notifyFinalVerdict(CaseEntry caseEntry) {
        List<User> allUsers = userRepository.findAll();
        allUsers.forEach(user -> createNotification(user, NotificationType.FINAL_VERDICT,
                "Final Verdict: " + caseEntry.getReferenceNumber(),
                "Case " + caseEntry.getClientName() + " has been " + caseEntry.getStatus().name(),
                caseEntry));
    }

    @Transactional(readOnly = true)
    public Page<Notification> getUserNotifications(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional
    public void markRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!notification.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllReadForUser(userId);
    }

    @Transactional
    public void notifyUserIfNotRecentlySent(
            User user,
            NotificationType type,
            String title,
            String body,
            CaseEntry caseEntry,
            LocalDateTime recentCutoff
    ) {
        if (caseEntry != null && notificationRepository.existsByUser_IdAndTypeAndCaseEntry_IdAndCreatedAtAfter(
                user.getId(), type, caseEntry.getId(), recentCutoff)) {
            return;
        }
        createNotification(user, type, title, body, caseEntry);
    }

    private void createNotification(User user, NotificationType type, String title, String body, CaseEntry caseEntry) {
        Notification n = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .body(body)
                .caseEntry(caseEntry)
                .build();
        n = notificationRepository.save(n);
        Object notificationPayload = java.util.Map.of(
                "id", n.getId().toString(),
                "userId", user.getId().toString(),
                "type", n.getType().name()
        );
        messagingTemplate.convertAndSendToUser(user.getId().toString(), "/queue/notifications", notificationPayload);
        pushNotificationService.send(user, type, title, body, caseEntry);
    }
}
