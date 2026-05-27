package com.equitycommittee.voting.domain.entity;

import com.equitycommittee.voting.domain.enums.VoteChoice;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "votes",
    uniqueConstraints = @UniqueConstraint(
        name = "unique_vote_per_user_per_case",
        columnNames = {"case_id", "voter_id"}
    ))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private CaseEntry caseEntry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voter_id", nullable = false)
    private User voter;

    @Enumerated(EnumType.STRING)
    @Column(name = "vote_choice", nullable = false)
    private VoteChoice voteChoice;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @CreationTimestamp
    @Column(name = "voted_at", updatable = false)
    private LocalDateTime votedAt;
}
