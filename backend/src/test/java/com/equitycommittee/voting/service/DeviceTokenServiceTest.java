package com.equitycommittee.voting.service;

import com.equitycommittee.voting.domain.entity.User;
import com.equitycommittee.voting.domain.entity.UserDeviceToken;
import com.equitycommittee.voting.domain.repository.UserDeviceTokenRepository;
import com.equitycommittee.voting.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserDeviceTokenRepository tokenRepository;

    private DeviceTokenService service;

    @BeforeEach
    void setUp() {
        service = new DeviceTokenService(userRepository, tokenRepository);
    }

    @Test
    void register_createsTokenForCurrentUser() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("user@equity.com")
                .password("secret")
                .fullName("User")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tokenRepository.findByToken("token-1")).thenReturn(Optional.empty());

        service.register(userId, "token-1", " android ");

        verify(tokenRepository).save(any(UserDeviceToken.class));
    }

    @Test
    void register_throwsUnauthorizedWhenUserMissing() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.register(userId, "token-1", "android"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void unregister_deletesMatchingUserToken() {
        UUID userId = UUID.randomUUID();

        service.unregister(userId, "token-1");

        verify(tokenRepository).deleteByUserIdAndToken(userId, "token-1");
    }
}
