package com.equitycommittee.voting.service;

import com.equitycommittee.voting.api.dto.cases.CaseResponse;
import com.equitycommittee.voting.api.dto.cases.CreateCaseRequest;
import com.equitycommittee.voting.api.dto.cases.UpdateCaseRequest;
import com.equitycommittee.voting.api.dto.cases.UpdateCaseStatusRequest;
import com.equitycommittee.voting.domain.entity.CaseEntry;
import com.equitycommittee.voting.domain.entity.CaseImage;
import com.equitycommittee.voting.domain.entity.User;
import com.equitycommittee.voting.domain.enums.CaseStatus;
import com.equitycommittee.voting.domain.enums.Role;
import com.equitycommittee.voting.domain.repository.AuditLogRepository;
import com.equitycommittee.voting.domain.repository.CaseImageRepository;
import com.equitycommittee.voting.domain.repository.CaseRepository;
import com.equitycommittee.voting.domain.repository.NotificationRepository;
import com.equitycommittee.voting.domain.repository.UserRepository;
import com.equitycommittee.voting.security.RolePermissions;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaseService {

    private final CaseRepository caseRepository;
    private final CaseImageRepository imageRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final S3Client s3Client;
    private static final Map<CaseStatus, Set<CaseStatus>> ALLOWED_TRANSITIONS = allowedTransitions();
    private static final Set<CaseStatus> COMMITTEE_HIDDEN_STATUSES =
            EnumSet.of(CaseStatus.DRAFT, CaseStatus.SUBMITTED);

    @Value("${app.s3.bucket}")
    private String bucket;

    @Transactional
    public CaseResponse createCase(CreateCaseRequest req) {
        User actor = currentUser();
        String refNumber = generateReferenceNumber();

        CaseEntry caseEntry = CaseEntry.builder()
                .referenceNumber(refNumber)
                .clientName(req.clientName())
                .requestedAmount(req.requestedAmount())
                .productType(req.productType())
                .tenure(req.tenure())
                .summary(req.summary())
                .riskNotes(req.riskNotes())
                .collateralSummary(req.collateralSummary())
                .votingDeadline(req.votingDeadline())
                .status(CaseStatus.DRAFT)
                .createdBy(actor)
                .build();

        caseEntry = caseRepository.saveAndFlush(caseEntry);
        auditService.log("CASE", caseEntry.getId(), "CREATED", actor, Map.of("referenceNumber", refNumber));
        notificationService.notifyCaseCreated(caseEntry);

        CaseResponse response = CaseResponse.from(caseEntry);
        publishCaseListUpdate(response);
        return response;
    }

    @Transactional(readOnly = true)
    public Page<CaseResponse> listCases(CaseStatus status, Pageable pageable) {
        User actor = currentUser();
        if (RolePermissions.isAdmin(actor)) {
            if (status != null) {
                return caseRepository.findByStatus(status, pageable).map(CaseResponse::from);
            }
            return caseRepository.findAll(pageable).map(CaseResponse::from);
        }

        if (RolePermissions.isManager(actor)) {
            if (status == CaseStatus.DRAFT) {
                return caseRepository.findByStatusAndCreatedById(CaseStatus.DRAFT, actor.getId(), pageable)
                        .map(CaseResponse::from);
            }
            if (status != null) {
                return caseRepository.findByStatus(status, pageable).map(CaseResponse::from);
            }
            return caseRepository.findByStatusNotOrCreatedById(CaseStatus.DRAFT, actor.getId(), pageable)
                    .map(CaseResponse::from);
        }

        if (RolePermissions.isCommitteeViewerRole(actor)) {
            if (status != null && COMMITTEE_HIDDEN_STATUSES.contains(status)) {
                return caseRepository.findByStatusAndCreatedById(status, actor.getId(), pageable)
                        .map(CaseResponse::from);
            }
            if (status != null) {
                return caseRepository.findByStatus(status, pageable).map(CaseResponse::from);
            }
            return caseRepository.findVisibleExcludingStatusesOrCreatedById(
                            COMMITTEE_HIDDEN_STATUSES,
                            actor.getId(),
                            pageable
                    )
                    .map(CaseResponse::from);
        }

        if (status != null) {
            return caseRepository.findByStatusAndCreatedById(status, actor.getId(), pageable).map(CaseResponse::from);
        }
        return caseRepository.findByCreatedById(actor.getId(), pageable).map(CaseResponse::from);
    }

    @Transactional(readOnly = true)
    public CaseResponse getCase(UUID id) {
        User actor = currentUser();
        CaseEntry caseEntry = findCaseOrThrow(id);
        assertCanViewCase(actor, caseEntry);
        return CaseResponse.from(caseEntry);
    }

    @Transactional
    public CaseResponse updateCase(UUID id, UpdateCaseRequest req) {
        User actor = currentUser();
        CaseEntry caseEntry = findCaseOrThrow(id);
        assertCanEditCase(actor, caseEntry);

        if (!isEditable(caseEntry.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Case cannot be edited once voting has opened or after final decision");
        }

        caseEntry.setClientName(req.clientName());
        caseEntry.setRequestedAmount(req.requestedAmount());
        caseEntry.setProductType(req.productType());
        caseEntry.setTenure(req.tenure());
        caseEntry.setSummary(req.summary());
        caseEntry.setRiskNotes(req.riskNotes());
        caseEntry.setCollateralSummary(req.collateralSummary());
        caseEntry.setVotingDeadline(req.votingDeadline());

        caseEntry = caseRepository.saveAndFlush(caseEntry);
        auditService.log("CASE", id, "UPDATED", actor, Map.of("status", caseEntry.getStatus().name()));
        CaseResponse response = CaseResponse.from(caseEntry);
        publishCaseListUpdate(response);
        return response;
    }

    @Transactional
    public CaseResponse updateStatus(UUID id, UpdateCaseStatusRequest req) {
        User actor = currentUser();
        CaseEntry caseEntry = findCaseOrThrow(id);
        CaseStatus previous = caseEntry.getStatus();

        validateStatusTransition(caseEntry, req.status());
        assertCanChangeStatus(actor, caseEntry, req.status());

        caseEntry.setStatus(req.status());
        if (isFinalStatus(req.status())) {
            caseEntry.setVerdict(req.status().name());
        }
        caseEntry = caseRepository.saveAndFlush(caseEntry);

        auditService.log("CASE", id, "STATUS_CHANGED", actor,
                Map.of("from", previous.name(), "to", req.status().name()));

        if (req.status() == CaseStatus.VOTING_OPEN) {
            notificationService.notifyVotingOpened(caseEntry);
        }

        CaseResponse response = CaseResponse.from(caseEntry);
        publishCaseListUpdate(response);
        return response;
    }

    @Transactional
    public void deleteCase(UUID id) {
        User actor = currentUser();
        if (actor.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can delete cases");
        }

        CaseEntry caseEntry = findCaseOrThrow(id);
        List<String> imageKeys = imageRepository.findByCaseEntryIdOrderBySortOrderAsc(id).stream()
                .map(CaseImage::getImageUrl)
                .filter(key -> key != null && !key.isBlank())
                .toList();

        for (String key : imageKeys) {
            deleteImageObject(key);
        }

        notificationRepository.deleteByCaseEntryId(id);
        auditLogRepository.deleteCaseAuditTrail(id.toString());
        caseRepository.delete(caseEntry);
        caseRepository.flush();
        publishCaseDeleted(id);
    }

    private CaseEntry findCaseOrThrow(UUID id) {
        return caseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));
    }

    private User currentUser() {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private String generateReferenceNumber() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String unique = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "EC-" + datePart + "-" + unique;
    }

    private void validateStatusTransition(CaseEntry caseEntry, CaseStatus requestedStatus) {
        CaseStatus current = caseEntry.getStatus();
        if (requestedStatus == current) {
            return;
        }

        Set<CaseStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(requestedStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid status transition: " + current + " -> " + requestedStatus);
        }

        if (requestedStatus == CaseStatus.VOTING_OPEN) {
            if (caseEntry.getVotingDeadline() == null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Voting deadline is required before opening voting");
            }
            if (!caseEntry.getVotingDeadline().isAfter(LocalDateTime.now())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Voting deadline must be in the future");
            }
        }
    }

    private boolean isEditable(CaseStatus status) {
        return status == CaseStatus.DRAFT
                || status == CaseStatus.SUBMITTED
                || status == CaseStatus.UNDER_REVIEW;
    }

    private boolean isFinalStatus(CaseStatus status) {
        return status == CaseStatus.APPROVED
                || status == CaseStatus.REJECTED
                || status == CaseStatus.DEFERRED;
    }

    private static Map<CaseStatus, Set<CaseStatus>> allowedTransitions() {
        Map<CaseStatus, Set<CaseStatus>> transitions = new EnumMap<>(CaseStatus.class);
        transitions.put(CaseStatus.DRAFT, EnumSet.of(CaseStatus.SUBMITTED));
        transitions.put(CaseStatus.SUBMITTED, EnumSet.of(CaseStatus.DRAFT, CaseStatus.UNDER_REVIEW));
        transitions.put(CaseStatus.UNDER_REVIEW, EnumSet.of(CaseStatus.VOTING_OPEN, CaseStatus.DEFERRED));
        transitions.put(CaseStatus.VOTING_OPEN, EnumSet.of(CaseStatus.APPROVED, CaseStatus.REJECTED, CaseStatus.DEFERRED));
        transitions.put(CaseStatus.APPROVED, EnumSet.of(CaseStatus.CLOSED));
        transitions.put(CaseStatus.REJECTED, EnumSet.of(CaseStatus.CLOSED));
        transitions.put(CaseStatus.DEFERRED, EnumSet.of(CaseStatus.CLOSED));
        transitions.put(CaseStatus.CLOSED, EnumSet.noneOf(CaseStatus.class));
        return transitions;
    }

    private void assertCanViewCase(User actor, CaseEntry caseEntry) {
        if (RolePermissions.isAdmin(actor) || RolePermissions.isCaseCreator(actor, caseEntry)) {
            return;
        }

        if (RolePermissions.isManager(actor) && caseEntry.getStatus() != CaseStatus.DRAFT) {
            return;
        }

        if (RolePermissions.isCommitteeViewerRole(actor) && RolePermissions.isManagerApproved(caseEntry)) {
            return;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to access case");
    }

    private void assertCanEditCase(User actor, CaseEntry caseEntry) {
        if (RolePermissions.isManagerApprovalRole(actor)) {
            return;
        }

        if (caseEntry.getStatus() == CaseStatus.UNDER_REVIEW && actor.getRole() == Role.CHAIRPERSON) {
            return;
        }

        if (RolePermissions.isCaseCreator(actor, caseEntry)
                && (caseEntry.getStatus() == CaseStatus.DRAFT || caseEntry.getStatus() == CaseStatus.SUBMITTED)) {
            return;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to modify this case");
    }

    private void assertCanChangeStatus(User actor, CaseEntry caseEntry, CaseStatus requestedStatus) {
        CaseStatus current = caseEntry.getStatus();
        if (requestedStatus == current) {
            return;
        }

        if (current == CaseStatus.DRAFT && requestedStatus == CaseStatus.SUBMITTED) {
            if (RolePermissions.isCaseCreator(actor, caseEntry) || RolePermissions.isManagerApprovalRole(actor)) {
                return;
            }
        }

        if (current == CaseStatus.SUBMITTED && requestedStatus == CaseStatus.DRAFT) {
            if (RolePermissions.isCaseCreator(actor, caseEntry) || RolePermissions.isManagerApprovalRole(actor)) {
                return;
            }
        }

        if (current == CaseStatus.SUBMITTED && requestedStatus == CaseStatus.UNDER_REVIEW) {
            if (RolePermissions.isManagerApprovalRole(actor)) {
                return;
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only managers can approve submitted cases");
        }

        if (current == CaseStatus.UNDER_REVIEW || current == CaseStatus.VOTING_OPEN || isFinalStatus(current)) {
            if (RolePermissions.isDecisionRole(actor)) {
                return;
            }
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to change this case status");
    }

    private void publishCaseListUpdate(CaseResponse response) {
        if (response.status() == CaseStatus.DRAFT || response.status() == CaseStatus.SUBMITTED) {
            return;
        }
        messagingTemplate.convertAndSend("/topic/cases", response);
    }

    private void publishCaseDeleted(UUID caseId) {
        messagingTemplate.convertAndSend("/topic/cases", (Object) Map.of(
                "caseId", caseId.toString(),
                "deleted", true
        ));
    }

    private void deleteImageObject(String key) {
        if (key.startsWith("http://") || key.startsWith("https://")) {
            return;
        }
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
