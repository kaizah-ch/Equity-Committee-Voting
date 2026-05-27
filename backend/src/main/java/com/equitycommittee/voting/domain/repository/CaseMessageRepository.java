package com.equitycommittee.voting.domain.repository;

import com.equitycommittee.voting.domain.entity.CaseMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CaseMessageRepository extends JpaRepository<CaseMessage, UUID> {
    Page<CaseMessage> findByCaseEntryIdOrderByCreatedAtAsc(UUID caseId, Pageable pageable);
}
