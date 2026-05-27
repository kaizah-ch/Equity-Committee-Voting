package com.equitycommittee.voting.domain.repository;

import com.equitycommittee.voting.domain.entity.CaseImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CaseImageRepository extends JpaRepository<CaseImage, UUID> {
    List<CaseImage> findByCaseEntryIdOrderBySortOrderAsc(UUID caseId);
    long countByCaseEntryId(UUID caseId);
}
