package com.equitycommittee.voting.domain.entity;

import com.equitycommittee.voting.domain.enums.CaseStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cases")
public class CaseEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reference_number", nullable = false, unique = true)
    private String referenceNumber;

    @Column(name = "client_name", nullable = false)
    private String clientName;

    @Column(name = "requested_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "product_type", nullable = false)
    private String productType;

    private String tenure;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "risk_notes", columnDefinition = "TEXT")
    private String riskNotes;

    @Column(name = "collateral_summary", columnDefinition = "TEXT")
    private String collateralSummary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseStatus status = CaseStatus.DRAFT;

    @Column(name = "voting_deadline")
    private LocalDateTime votingDeadline;

    private String verdict;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Getters and Setters for used fields
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public void setRequestedAmount(BigDecimal requestedAmount) {
        this.requestedAmount = requestedAmount;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public CaseStatus getStatus() {
        return status;
    }

    public void setStatus(CaseStatus status) {
        this.status = status;
    }

    public String getTenure() {
        return tenure;
    }

    public void setTenure(String tenure) {
        this.tenure = tenure;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getRiskNotes() {
        return riskNotes;
    }

    public void setRiskNotes(String riskNotes) {
        this.riskNotes = riskNotes;
    }

    public String getCollateralSummary() {
        return collateralSummary;
    }

    public void setCollateralSummary(String collateralSummary) {
        this.collateralSummary = collateralSummary;
    }

    public LocalDateTime getVotingDeadline() {
        return votingDeadline;
    }

    public void setVotingDeadline(LocalDateTime votingDeadline) {
        this.votingDeadline = votingDeadline;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Builder
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private String referenceNumber;
        private String clientName;
        private BigDecimal requestedAmount;
        private String productType;
        private String tenure;
        private String summary;
        private String riskNotes;
        private String collateralSummary;
        private CaseStatus status = CaseStatus.DRAFT;
        private LocalDateTime votingDeadline;
        private String verdict;
        private User createdBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder referenceNumber(String referenceNumber) {
            this.referenceNumber = referenceNumber;
            return this;
        }

        public Builder clientName(String clientName) {
            this.clientName = clientName;
            return this;
        }

        public Builder requestedAmount(BigDecimal requestedAmount) {
            this.requestedAmount = requestedAmount;
            return this;
        }

        public Builder productType(String productType) {
            this.productType = productType;
            return this;
        }

        public Builder tenure(String tenure) {
            this.tenure = tenure;
            return this;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder riskNotes(String riskNotes) {
            this.riskNotes = riskNotes;
            return this;
        }

        public Builder collateralSummary(String collateralSummary) {
            this.collateralSummary = collateralSummary;
            return this;
        }

        public Builder status(CaseStatus status) {
            this.status = status;
            return this;
        }

        public Builder votingDeadline(LocalDateTime votingDeadline) {
            this.votingDeadline = votingDeadline;
            return this;
        }

        public Builder verdict(String verdict) {
            this.verdict = verdict;
            return this;
        }

        public Builder createdBy(User createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public CaseEntry build() {
            CaseEntry caseEntry = new CaseEntry();
            caseEntry.id = this.id;
            caseEntry.referenceNumber = this.referenceNumber;
            caseEntry.clientName = this.clientName;
            caseEntry.requestedAmount = this.requestedAmount;
            caseEntry.productType = this.productType;
            caseEntry.tenure = this.tenure;
            caseEntry.summary = this.summary;
            caseEntry.riskNotes = this.riskNotes;
            caseEntry.collateralSummary = this.collateralSummary;
            caseEntry.status = this.status;
            caseEntry.votingDeadline = this.votingDeadline;
            caseEntry.verdict = this.verdict;
            caseEntry.createdBy = this.createdBy;
            caseEntry.createdAt = this.createdAt;
            caseEntry.updatedAt = this.updatedAt;
            return caseEntry;
        }
    }
}
