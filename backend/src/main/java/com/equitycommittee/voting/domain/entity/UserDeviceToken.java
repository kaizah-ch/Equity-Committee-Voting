package com.equitycommittee.voting.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_device_tokens")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserDeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 4096)
    private String token;

    @Column(length = 50)
    private String platform;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
