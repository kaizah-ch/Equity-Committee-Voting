package com.equitycommittee.voting.service;

import com.equitycommittee.voting.domain.entity.User;
import com.equitycommittee.voting.domain.entity.UserDeviceToken;
import com.equitycommittee.voting.domain.enums.NotificationType;
import com.equitycommittee.voting.domain.repository.UserDeviceTokenRepository;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseException;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    @Mock
    private UserDeviceTokenRepository tokenRepository;

    @Mock
    private ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    private PushNotificationService service;

    @BeforeEach
    void setUp() {
        service = new PushNotificationService(tokenRepository, firebaseMessagingProvider);
    }

    @Test
    void send_removesUnregisteredToken() throws Exception {
        User user = user();
        UserDeviceToken token = token(user, "token-1");
        when(firebaseMessagingProvider.getIfAvailable()).thenReturn(firebaseMessaging);
        when(tokenRepository.findByUserId(user.getId())).thenReturn(List.of(token));
        when(firebaseMessaging.send(any(Message.class))).thenThrow(firebaseException(MessagingErrorCode.UNREGISTERED));

        service.send(user, NotificationType.CASE_CREATED, "Title", "Body", null);

        verify(tokenRepository).delete(token);
    }

    @Test
    void send_retriesTransientFailureOnce() throws Exception {
        User user = user();
        UserDeviceToken token = token(user, "token-1");
        when(firebaseMessagingProvider.getIfAvailable()).thenReturn(firebaseMessaging);
        when(tokenRepository.findByUserId(user.getId())).thenReturn(List.of(token));
        when(firebaseMessaging.send(any(Message.class)))
                .thenThrow(firebaseException(MessagingErrorCode.UNAVAILABLE))
                .thenReturn("message-id");

        service.send(user, NotificationType.CASE_CREATED, "Title", "Body", null);

        verify(firebaseMessaging, org.mockito.Mockito.times(2)).send(any(Message.class));
        verify(tokenRepository, never()).delete(any(UserDeviceToken.class));
    }

    @Test
    void send_skipsWhenFirebaseIsDisabled() {
        User user = user();
        when(firebaseMessagingProvider.getIfAvailable()).thenReturn(null);

        service.send(user, NotificationType.CASE_CREATED, "Title", "Body", null);

        verify(tokenRepository, never()).findByUserId(user.getId());
    }

    private FirebaseMessagingException firebaseException(MessagingErrorCode code) {
        try {
            FirebaseException cause = new FirebaseException(errorCodeFor(code), code.name(), null);
            Method method = FirebaseMessagingException.class.getDeclaredMethod(
                    "withMessagingErrorCode",
                    FirebaseException.class,
                    MessagingErrorCode.class);
            method.setAccessible(true);
            return (FirebaseMessagingException) method.invoke(null, cause, code);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private ErrorCode errorCodeFor(MessagingErrorCode code) {
        return switch (code) {
            case INVALID_ARGUMENT, UNREGISTERED -> ErrorCode.INVALID_ARGUMENT;
            case INTERNAL -> ErrorCode.INTERNAL;
            case UNAVAILABLE -> ErrorCode.UNAVAILABLE;
            case QUOTA_EXCEEDED -> ErrorCode.RESOURCE_EXHAUSTED;
            case THIRD_PARTY_AUTH_ERROR, SENDER_ID_MISMATCH -> ErrorCode.PERMISSION_DENIED;
        };
    }

    private User user() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("user@equity.com")
                .password("secret")
                .fullName("User")
                .build();
    }

    private UserDeviceToken token(User user, String value) {
        return UserDeviceToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .token(value)
                .platform("android")
                .build();
    }
}
