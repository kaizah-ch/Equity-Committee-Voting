package com.equitycommittee.voting.domain.repository;

import com.equitycommittee.voting.domain.entity.Notification;
import com.equitycommittee.voting.domain.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    long countByUserIdAndReadFalse(UUID userId);
    boolean existsByUser_IdAndTypeAndCaseEntry_IdAndCreatedAtAfter(
            UUID userId,
            NotificationType type,
            UUID caseId,
            LocalDateTime after);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.user.id = :userId")
    void markAllReadForUser(UUID userId);
}
