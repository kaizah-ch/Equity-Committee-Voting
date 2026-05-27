package com.equitycommittee.voting.service;

import com.equitycommittee.voting.api.dto.auth.LoginRequest;
import com.equitycommittee.voting.api.dto.auth.TokenResponse;
import com.equitycommittee.voting.domain.entity.RefreshToken;
import com.equitycommittee.voting.domain.entity.User;
import com.equitycommittee.voting.domain.repository.RefreshTokenRepository;
import com.equitycommittee.voting.domain.repository.UserRepository;
import com.equitycommittee.voting.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public TokenResponse login(LoginRequest request) {
        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (BadCredentialsException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        return issueTokenPair(user);
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        if (!tokenProvider.validateRefreshToken(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());

        RefreshToken stored = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is not recognized"));

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(stored);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token has expired");
        }

        UUID userIdFromToken = tokenProvider.getUserId(refreshToken);
        if (!stored.getUser().getId().equals(userIdFromToken)) {
            refreshTokenRepository.delete(stored);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid");
        }

        User user = userRepository.findById(userIdFromToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
        if (!user.isActive()) {
            refreshTokenRepository.delete(stored);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account is inactive");
        }

        // Rotate refresh token on every use.
        refreshTokenRepository.delete(stored);
        return issueTokenPair(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        // Idempotent logout: silently succeed when token is absent.
        refreshTokenRepository.findByToken(refreshToken)
                .ifPresent(refreshTokenRepository::delete);
    }

    private TokenResponse issueTokenPair(User user) {
        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = tokenProvider.generateRefreshToken(user.getId(), user.getEmail(), user.getRole().name());

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .token(refreshToken)
                .expiresAt(LocalDateTime.now().plusSeconds(tokenProvider.getRefreshExpirationMs() / 1000))
                .build());

        return TokenResponse.of(accessToken, refreshToken, tokenProvider.getAccessExpirationMs() / 1000);
    }
}
