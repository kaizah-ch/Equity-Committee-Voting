package com.equitycommittee.voting.service;

import com.equitycommittee.voting.domain.entity.User;
import com.equitycommittee.voting.domain.entity.UserDeviceToken;
import com.equitycommittee.voting.domain.repository.UserDeviceTokenRepository;
import com.equitycommittee.voting.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final UserRepository userRepository;
    private final UserDeviceTokenRepository tokenRepository;

    @Transactional
    public void register(UUID userId, String token, String platform) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        UserDeviceToken deviceToken = tokenRepository.findByToken(token)
                .orElseGet(UserDeviceToken::new);
        deviceToken.setUser(user);
        deviceToken.setToken(token);
        deviceToken.setPlatform(normalize(platform));
        deviceToken.setLastSeenAt(LocalDateTime.now());
        tokenRepository.save(deviceToken);
    }

    @Transactional
    public void unregister(UUID userId, String token) {
        tokenRepository.deleteByUserIdAndToken(userId, token);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
