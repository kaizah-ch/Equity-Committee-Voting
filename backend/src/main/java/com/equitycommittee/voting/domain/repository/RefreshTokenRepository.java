package com.equitycommittee.voting.domain.repository;

import com.equitycommittee.voting.domain.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByToken(String token);
    void deleteByUserId(UUID userId);
    void deleteByExpiresAtBefore(LocalDateTime cutoff);
}

