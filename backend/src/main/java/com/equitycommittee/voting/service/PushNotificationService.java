package com.equitycommittee.voting.service;

import com.equitycommittee.voting.domain.entity.CaseEntry;
import com.equitycommittee.voting.domain.entity.User;
import com.equitycommittee.voting.domain.entity.UserDeviceToken;
import com.equitycommittee.voting.domain.enums.NotificationType;
import com.equitycommittee.voting.domain.repository.UserDeviceTokenRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationService {

    private final UserDeviceTokenRepository tokenRepository;
    private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;

    @Async
    public void send(User user, NotificationType type, String title, String body, CaseEntry caseEntry) {
        FirebaseMessaging firebaseMessaging = firebaseMessagingProvider.getIfAvailable();
        if (firebaseMessaging == null || user == null || user.getId() == null) {
            return;
        }

        Map<String, String> data = new HashMap<>();
        data.put("type", type.name());
        if (caseEntry != null && caseEntry.getId() != null) {
            data.put("caseId", caseEntry.getId().toString());
        }

        for (UserDeviceToken deviceToken : tokenRepository.findByUserId(user.getId())) {
            try {
                Message message = Message.builder()
                        .setToken(deviceToken.getToken())
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .putAllData(data)
                        .build();
                sendWithRetry(firebaseMessaging, message);
            } catch (FirebaseMessagingException ex) {
                if (isInvalidToken(ex)) {
                    tokenRepository.delete(deviceToken);
                    log.info("Removed invalid FCM token for user {}", user.getId());
                    continue;
                }
                log.warn("Failed to send FCM notification to user {}: {}", user.getId(), ex.getMessage());
            } catch (Exception ex) {
                log.warn("Failed to send FCM notification to user {}: {}", user.getId(), ex.getMessage());
            }
        }
    }

    private void sendWithRetry(FirebaseMessaging firebaseMessaging, Message message) throws FirebaseMessagingException {
        try {
            firebaseMessaging.send(message);
        } catch (FirebaseMessagingException ex) {
            if (!isTransient(ex)) {
                throw ex;
            }
            firebaseMessaging.send(message);
        }
    }

    private boolean isInvalidToken(FirebaseMessagingException ex) {
        MessagingErrorCode errorCode = ex.getMessagingErrorCode();
        return errorCode == MessagingErrorCode.UNREGISTERED
                || errorCode == MessagingErrorCode.INVALID_ARGUMENT;
    }

    private boolean isTransient(FirebaseMessagingException ex) {
        MessagingErrorCode errorCode = ex.getMessagingErrorCode();
        return errorCode == MessagingErrorCode.UNAVAILABLE
                || errorCode == MessagingErrorCode.INTERNAL
                || errorCode == MessagingErrorCode.QUOTA_EXCEEDED;
    }
}
