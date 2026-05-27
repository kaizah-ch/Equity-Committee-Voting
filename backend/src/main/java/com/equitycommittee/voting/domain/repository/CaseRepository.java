package com.equitycommittee.voting.domain.repository;

import com.equitycommittee.voting.domain.entity.CaseEntry;
import com.equitycommittee.voting.domain.enums.CaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface CaseRepository extends JpaRepository<CaseEntry, UUID> {
    Page<CaseEntry> findByStatus(CaseStatus status, Pageable pageable);
    Page<CaseEntry> findByStatusAndCreatedById(CaseStatus status, UUID userId, Pageable pageable);
    Page<CaseEntry> findByStatusNotOrCreatedById(CaseStatus status, UUID userId, Pageable pageable);
    List<CaseEntry> findByStatus(CaseStatus status);
    Page<CaseEntry> findByCreatedById(UUID userId, Pageable pageable);
    boolean existsByReferenceNumber(String referenceNumber);
    List<CaseEntry> findByStatusAndVotingDeadlineBefore(CaseStatus status, LocalDateTime deadline);
    List<CaseEntry> findByStatusAndVotingDeadlineBetween(CaseStatus status, LocalDateTime start, LocalDateTime end);
}
