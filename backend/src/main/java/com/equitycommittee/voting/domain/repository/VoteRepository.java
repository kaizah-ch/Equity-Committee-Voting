package com.equitycommittee.voting.domain.repository;

import com.equitycommittee.voting.domain.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoteRepository extends JpaRepository<Vote, UUID> {
    List<Vote> findByCaseEntryId(UUID caseId);
    Optional<Vote> findByCaseEntryIdAndVoterId(UUID caseId, UUID voterId);
    boolean existsByCaseEntryIdAndVoterId(UUID caseId, UUID voterId);
}
