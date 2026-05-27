package com.equitycommittee.voting.service;

import com.equitycommittee.voting.domain.entity.Notification;
import com.equitycommittee.voting.domain.entity.User;
import com.equitycommittee.voting.domain.repository.NotificationRepository;
import com.equitycommittee.voting.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private PushNotificationService pushNotificationService;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository,
                userRepository,
                messagingTemplate,
                pushNotificationService
        );
    }

    @Test
    void markRead_marksNotificationAsRead_whenOwnerMatches() {
        UUID notificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Notification notification = notificationOwnedBy(userId);

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        notificationService.markRead(notificationId, userId);

        verify(notificationRepository).save(notification);
        assertEquals(true, notification.isRead());
    }

    @Test
    void markRead_throwsNotFound_whenNotificationMissing() {
        UUID notificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> notificationService.markRead(notificationId, userId)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(notificationRepository, never()).save(org.mockito.ArgumentMatchers.any(Notification.class));
    }

    @Test
    void markRead_throwsForbidden_whenNotificationOwnedByDifferentUser() {
        UUID notificationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Notification notification = notificationOwnedBy(ownerId);

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> notificationService.markRead(notificationId, callerId)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(notificationRepository, never()).save(org.mockito.ArgumentMatchers.any(Notification.class));
    }

    @Test
    void markAllRead_delegatesToRepository() {
        UUID userId = UUID.randomUUID();

        notificationService.markAllRead(userId);

        verify(notificationRepository).markAllReadForUser(userId);
    }

    private Notification notificationOwnedBy(UUID userId) {
        User user = User.builder()
                .id(userId)
                .email("owner@equity.com")
                .password("secret")
                .fullName("Owner User")
                .build();

        return Notification.builder()
                .id(UUID.randomUUID())
                .user(user)
                .title("Title")
                .body("Body")
                .build();
    }
}
