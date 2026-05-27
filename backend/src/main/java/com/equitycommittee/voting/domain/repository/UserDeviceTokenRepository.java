package com.equitycommittee.voting.domain.repository;

import com.equitycommittee.voting.domain.entity.UserDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDeviceTokenRepository extends JpaRepository<UserDeviceToken, UUID> {
    List<UserDeviceToken> findByUserId(UUID userId);
    Optional<UserDeviceToken> findByToken(String token);
    void deleteByUserIdAndToken(UUID userId, String token);
}
