package com.equitycommittee.voting.api.controller;

import com.equitycommittee.voting.domain.entity.CaseEntry;
import com.equitycommittee.voting.domain.entity.Notification;
import com.equitycommittee.voting.domain.entity.User;
import com.equitycommittee.voting.domain.enums.NotificationType;
import com.equitycommittee.voting.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    private NotificationController controller;

    @BeforeEach
    void setUp() {
        controller = new NotificationController(notificationService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getNotifications_usesAuthenticatedUserId() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID notificationId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID caseId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        Pageable pageable = Pageable.ofSize(20);
        Notification notification = Notification.builder()
                .id(notificationId)
                .user(User.builder().id(userId).build())
                .type(NotificationType.VOTING_OPENED)
                .title("Voting Open")
                .body("Voting is open")
                .caseEntry(CaseEntry.builder().id(caseId).build())
                .read(false)
                .createdAt(LocalDateTime.of(2026, 5, 18, 14, 0))
                .build();
        var page = new PageImpl<>(List.of(notification), pageable, 1);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId.toString(), null)
        );
        when(notificationService.getUserNotifications(userId, pageable)).thenReturn(page);

        var response = controller.getNotifications(pageable);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var body = response.getBody();
        assertEquals(1, body.getTotalElements());
        var dto = body.getContent().getFirst();
        assertEquals(notificationId, dto.id());
        assertEquals(userId, dto.userId());
        assertEquals(NotificationType.VOTING_OPENED, dto.type());
        assertEquals("Voting Open", dto.title());
        assertEquals("Voting is open", dto.body());
        assertEquals(caseId, dto.caseId());
        assertEquals(false, dto.isRead());
        assertEquals(LocalDateTime.of(2026, 5, 18, 14, 0), dto.createdAt());
        verify(notificationService).getUserNotifications(userId, pageable);
    }

    @Test
    void markRead_returnsNoContentAndDelegatesUserId() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID notificationId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId.toString(), null)
        );

        var response = controller.markRead(notificationId);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(notificationService).markRead(notificationId, userId);
    }

    @Test
    void markAllRead_returnsNoContentAndDelegatesUserId() {
        UUID userId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId.toString(), null)
        );

        var response = controller.markAllRead();

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(notificationService).markAllRead(userId);
    }
}
