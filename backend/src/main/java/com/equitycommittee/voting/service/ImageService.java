package com.equitycommittee.voting.service;

import com.equitycommittee.voting.api.dto.images.ImageResponse;
import com.equitycommittee.voting.domain.entity.CaseEntry;
import com.equitycommittee.voting.domain.entity.CaseImage;
import com.equitycommittee.voting.domain.entity.User;
import com.equitycommittee.voting.domain.enums.CaseStatus;
import com.equitycommittee.voting.domain.enums.Role;
import com.equitycommittee.voting.domain.repository.CaseImageRepository;
import com.equitycommittee.voting.domain.repository.CaseRepository;
import com.equitycommittee.voting.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ImageService {

    private final CaseImageRepository imageRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${app.s3.bucket}") private String bucket;
    @Value("${app.s3.presigned-url-minutes:15}") private int presignedUrlMinutes;
    @Value("${app.images.max-per-case}") private int maxImages;
    @Value("${app.images.max-size-mb:10}") private int maxImageSizeMb;
    @Value("#{'${app.images.allowed-content-types:image/jpeg,image/png,image/webp}'.split(',')}")
    private List<String> allowedContentTypes;

    @Transactional
    public ImageResponse uploadImage(UUID caseId, MultipartFile file, String caption) throws IOException {
        User uploader = currentUser();
        CaseEntry caseEntry = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));
        assertCanUploadImage(uploader, caseEntry);

        if (imageRepository.countByCaseEntryId(caseId) >= maxImages) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Maximum of " + maxImages + " images per case reached");
        }
        validateUpload(file);

        String originalFilename = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "image-upload"
                : file.getOriginalFilename().replace("\\", "_").replace("/", "_");
        String key = "cases/" + caseId + "/" + UUID.randomUUID() + "-" + originalFilename;
        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key)
                        .contentType(file.getContentType()).build(),
                RequestBody.fromBytes(file.getBytes())
        );

        int sortOrder = (int) imageRepository.countByCaseEntryId(caseId);
        CaseImage image = CaseImage.builder()
                .caseEntry(caseEntry)
                .uploadedBy(uploader)
                .imageUrl(key)
                .caption(caption)
                .sortOrder(sortOrder)
                .build();

        image = imageRepository.save(image);
        auditService.log("IMAGE", image.getId(), "UPLOADED", uploader, Map.of("caseId", caseId.toString(), "key", key));
        return toResponse(image);
    }

    @Transactional(readOnly = true)
    public List<ImageResponse> getImages(UUID caseId) {
        User actor = currentUser();
        CaseEntry caseEntry = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));
        assertCanViewImages(actor, caseEntry);

        return imageRepository.findByCaseEntryIdOrderBySortOrderAsc(caseId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteImage(UUID imageId) {
        User actor = currentUser();
        CaseImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found"));
        assertCanMutateImage(actor, image);

        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(image.getImageUrl()).build());
        imageRepository.delete(image);
        auditService.log("IMAGE", imageId, "DELETED", actor, Map.of("key", image.getImageUrl()));
    }

    @Transactional
    public ImageResponse updateCaption(UUID imageId, String caption) {
        User actor = currentUser();
        CaseImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found"));
        assertCanMutateImage(actor, image);

        String normalizedCaption = normalizeCaption(caption);
        image.setCaption(normalizedCaption);
        image = imageRepository.save(image);
        auditService.log("IMAGE", imageId, "CAPTION_UPDATED", actor, Map.of(
                "caseId", image.getCaseEntry().getId().toString(),
                "caption", normalizedCaption == null ? "" : normalizedCaption
        ));
        return toResponse(image);
    }

    private User currentUser() {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private void validateUpload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is required");
        }

        long maxBytes = (long) maxImageSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Image exceeds max size of " + maxImageSizeMb + " MB");
        }

        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT).trim();
        Set<String> allowed = allowedContentTypes.stream()
                .map(v -> v.toLowerCase(Locale.ROOT).trim())
                .collect(Collectors.toSet());

        if (!allowed.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Unsupported image type. Allowed: " + String.join(", ", allowed));
        }
    }

    private ImageResponse toResponse(CaseImage image) {
        return ImageResponse.builder()
                .id(image.getId())
                .caseId(image.getCaseEntry().getId())
                .uploadedById(image.getUploadedBy().getId())
                .imageUrl(buildReadUrl(image.getImageUrl()))
                .caption(image.getCaption())
                .sortOrder(image.getSortOrder())
                .createdAt(image.getCreatedAt())
                .build();
    }

    private String normalizeCaption(String caption) {
        if (caption == null) {
            return null;
        }
        String trimmed = caption.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void assertCanMutateImage(User actor, CaseImage image) {
        boolean isUploader = image.getUploadedBy() != null
                && image.getUploadedBy().getId() != null
                && image.getUploadedBy().getId().equals(actor.getId());
        boolean isCaseCreator = image.getCaseEntry() != null
                && image.getCaseEntry().getCreatedBy() != null
                && image.getCaseEntry().getCreatedBy().getId() != null
                && image.getCaseEntry().getCreatedBy().getId().equals(actor.getId());
        boolean isPrivilegedRole = actor.getRole() == Role.ADMIN || actor.getRole() == Role.CHAIRPERSON;

        if (!isUploader && !isCaseCreator && !isPrivilegedRole) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to modify this image");
        }
    }

    private void assertCanUploadImage(User actor, CaseEntry caseEntry) {
        boolean isCaseCreator = caseEntry.getCreatedBy() != null
                && caseEntry.getCreatedBy().getId() != null
                && caseEntry.getCreatedBy().getId().equals(actor.getId());
        boolean isPrivilegedRole = actor.getRole() == Role.ADMIN || actor.getRole() == Role.CHAIRPERSON;

        if (!isCaseCreator && !isPrivilegedRole) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to upload image for this case");
        }
    }

    private void assertCanViewImages(User actor, CaseEntry caseEntry) {
        boolean isCaseCreator = caseEntry.getCreatedBy() != null
                && caseEntry.getCreatedBy().getId() != null
                && caseEntry.getCreatedBy().getId().equals(actor.getId());
        boolean isPrivilegedRole = actor.getRole() == Role.ADMIN || actor.getRole() == Role.CHAIRPERSON;
        if (isCaseCreator || isPrivilegedRole) {
            return;
        }

        if (caseEntry.getStatus() == CaseStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to view draft case images");
        }

        boolean isCommitteeRole = actor.getRole() == Role.COMMITTEE_MEMBER || actor.getRole() == Role.SECRETARY;
        if (!isCommitteeRole) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to view case images");
        }
    }

    private String buildReadUrl(String key) {
        if (key == null || key.isBlank()) {
            return key;
        }
        if (key.startsWith("http://") || key.startsWith("https://")) {
            return key;
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignedUrlMinutes))
                .getObjectRequest(getObjectRequest)
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }
}
