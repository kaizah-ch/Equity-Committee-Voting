package com.equitycommittee.voting.service;

import com.equitycommittee.voting.domain.entity.CaseEntry;
import com.equitycommittee.voting.domain.entity.CaseImage;
import com.equitycommittee.voting.domain.entity.User;
import com.equitycommittee.voting.domain.enums.CaseStatus;
import com.equitycommittee.voting.domain.enums.Role;
import com.equitycommittee.voting.domain.repository.CaseImageRepository;
import com.equitycommittee.voting.domain.repository.CaseRepository;
import com.equitycommittee.voting.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    @Mock
    private CaseImageRepository imageRepository;
    @Mock
    private CaseRepository caseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private S3Client s3Client;
    @Mock
    private S3Presigner s3Presigner;

    private ImageService imageService;
    private UUID userId;
    private UUID caseId;

    @BeforeEach
    void setUp() throws Exception {
        imageService = new ImageService(imageRepository, caseRepository, userRepository, auditService, s3Client, s3Presigner);
        ReflectionTestUtils.setField(imageService, "bucket", "test-bucket");
        ReflectionTestUtils.setField(imageService, "presignedUrlMinutes", 15);
        ReflectionTestUtils.setField(imageService, "maxImages", 40);
        ReflectionTestUtils.setField(imageService, "maxImageSizeMb", 10);
        ReflectionTestUtils.setField(imageService, "allowedContentTypes", List.of("image/jpeg", "image/png", "image/webp"));

        userId = UUID.randomUUID();
        caseId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId.toString(), null)
        );

        User user = User.builder()
                .id(userId)
                .email("u@equity.com")
                .password("secret")
                .fullName("Uploader")
                .role(Role.COMMITTEE_MEMBER)
                .build();
        CaseEntry caseEntry = CaseEntry.builder()
                .id(caseId)
                .referenceNumber("ECV-1")
                .clientName("Client")
                .requestedAmount(BigDecimal.ONE)
                .productType("TERM_LOAN")
                .createdBy(user)
                .createdAt(LocalDateTime.now())
                .build();

        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        lenient().when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseEntry));
        lenient().when(imageRepository.countByCaseEntryId(caseId)).thenReturn(0L);

        PresignedGetObjectRequest presignedGetObjectRequest = mock(PresignedGetObjectRequest.class);
        lenient().when(presignedGetObjectRequest.url()).thenReturn(new URL("https://example.com/image.jpg"));
        lenient().when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedGetObjectRequest);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadImage_rejectsUnsupportedContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "file.gif", "image/gif", new byte[]{1, 2, 3}
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> imageService.uploadImage(caseId, file, null));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getStatusCode());
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
    }

    @Test
    void uploadImage_rejectsFileLargerThanConfiguredLimit() {
        byte[] bytes = new byte[(10 * 1024 * 1024) + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.jpg", "image/jpeg", bytes
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> imageService.uploadImage(caseId, file, null));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, ex.getStatusCode());
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
    }

    @Test
    void uploadImage_rejectsWhenCaseAlreadyHasMaximumImages() {
        when(imageRepository.countByCaseEntryId(caseId)).thenReturn(40L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "ok.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> imageService.uploadImage(caseId, file, null));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
        assertEquals("Maximum of 40 images per case reached", ex.getReason());
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
        verify(imageRepository, never()).save(any(CaseImage.class));
    }

    @Test
    void uploadImage_forbiddenForUnrelatedUser() {
        UUID actorId = UUID.randomUUID();
        User actor = User.builder()
                .id(actorId)
                .email("other@equity.com")
                .password("secret")
                .fullName("Other")
                .role(Role.COMMITTEE_MEMBER)
                .build();
        User caseCreator = User.builder()
                .id(UUID.randomUUID())
                .email("creator@equity.com")
                .password("secret")
                .fullName("Creator")
                .role(Role.COMMITTEE_MEMBER)
                .build();
        CaseEntry caseEntry = CaseEntry.builder()
                .id(caseId)
                .referenceNumber("ECV-2")
                .clientName("Client")
                .requestedAmount(BigDecimal.ONE)
                .productType("TERM_LOAN")
                .createdBy(caseCreator)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null)
        );
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseEntry));

        MockMultipartFile file = new MockMultipartFile(
                "file", "ok.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> imageService.uploadImage(caseId, file, null));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
        verify(imageRepository, never()).save(any(CaseImage.class));
    }

    @Test
    void uploadImage_allowedForAdminWhenNotCaseCreator() {
        UUID actorId = UUID.randomUUID();
        User actor = User.builder()
                .id(actorId)
                .email("admin@equity.com")
                .password("secret")
                .fullName("Admin")
                .role(Role.ADMIN)
                .build();
        User caseCreator = User.builder()
                .id(UUID.randomUUID())
                .email("creator@equity.com")
                .password("secret")
                .fullName("Creator")
                .role(Role.COMMITTEE_MEMBER)
                .build();
        CaseEntry caseEntry = CaseEntry.builder()
                .id(caseId)
                .referenceNumber("ECV-3")
                .clientName("Client")
                .requestedAmount(BigDecimal.ONE)
                .productType("TERM_LOAN")
                .createdBy(caseCreator)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null)
        );
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseEntry));
        when(imageRepository.save(any(CaseImage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file", "ok.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );

        assertDoesNotThrow(() -> imageService.uploadImage(caseId, file, "caption"));
        verify(imageRepository).save(any(CaseImage.class));
    }

    @Test
    void uploadImage_allowedForChairpersonWhenNotCaseCreator() {
        UUID actorId = UUID.randomUUID();
        User actor = User.builder()
                .id(actorId)
                .email("chair@equity.com")
                .password("secret")
                .fullName("Chair")
                .role(Role.CHAIRPERSON)
                .build();
        User caseCreator = User.builder()
                .id(UUID.randomUUID())
                .email("creator@equity.com")
                .password("secret")
                .fullName("Creator")
                .role(Role.COMMITTEE_MEMBER)
                .build();
        CaseEntry caseEntry = CaseEntry.builder()
                .id(caseId)
                .referenceNumber("ECV-4")
                .clientName("Client")
                .requestedAmount(BigDecimal.ONE)
                .productType("TERM_LOAN")
                .createdBy(caseCreator)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null)
        );
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseEntry));
        when(imageRepository.save(any(CaseImage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file", "ok.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );

        assertDoesNotThrow(() -> imageService.uploadImage(caseId, file, "caption"));
        verify(imageRepository).save(any(CaseImage.class));
    }

    @Test
    void uploadImage_forbiddenForSecretaryWhenNotCaseCreator() {
        UUID actorId = UUID.randomUUID();
        User actor = User.builder()
                .id(actorId)
                .email("secretary@equity.com")
                .password("secret")
                .fullName("Secretary")
                .role(Role.SECRETARY)
                .build();
        User caseCreator = User.builder()
                .id(UUID.randomUUID())
                .email("creator@equity.com")
                .password("secret")
                .fullName("Creator")
                .role(Role.COMMITTEE_MEMBER)
                .build();
        CaseEntry caseEntry = CaseEntry.builder()
                .id(caseId)
                .referenceNumber("ECV-5")
                .clientName("Client")
                .requestedAmount(BigDecimal.ONE)
                .productType("TERM_LOAN")
                .createdBy(caseCreator)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null)
        );
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseEntry));

        MockMultipartFile file = new MockMultipartFile(
                "file", "ok.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> imageService.uploadImage(caseId, file, null));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
        verify(imageRepository, never()).save(any(CaseImage.class));
    }

    @Test
    void getImages_forbiddenForUnrelatedUserWhenCaseIsDraft() {
        UUID actorId = UUID.randomUUID();
        User actor = User.builder()
                .id(actorId)
                .email("member@equity.com")
                .password("secret")
                .fullName("Member")
                .role(Role.COMMITTEE_MEMBER)
                .build();
        User creator = User.builder()
                .id(UUID.randomUUID())
                .email("creator@equity.com")
                .password("secret")
                .fullName("Creator")
                .role(Role.COMMITTEE_MEMBER)
                .build();
        CaseEntry draftCase = CaseEntry.builder()
                .id(caseId)
                .status(CaseStatus.DRAFT)
                .createdBy(creator)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null)
        );
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(draftCase));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> imageService.getImages(caseId));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(imageRepository, never()).findByCaseEntryIdOrderBySortOrderAsc(caseId);
    }

    @Test
    void getImages_allowedForCommitteeMemberWhenCaseNotDraft() {
        UUID actorId = UUID.randomUUID();
        User actor = User.builder()
                .id(actorId)
                .email("member@equity.com")
                .password("secret")
                .fullName("Member")
                .role(Role.COMMITTEE_MEMBER)
                .build();
        User creator = User.builder()
                .id(UUID.randomUUID())
                .email("creator@equity.com")
                .password("secret")
                .fullName("Creator")
                .role(Role.COMMITTEE_MEMBER)
                .build();
        CaseEntry submittedCase = CaseEntry.builder()
                .id(caseId)
                .status(CaseStatus.SUBMITTED)
                .createdBy(creator)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null)
        );
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(submittedCase));
        when(imageRepository.findByCaseEntryIdOrderBySortOrderAsc(caseId)).thenReturn(List.of());

        List<?> images = imageService.getImages(caseId);

        assertTrue(images.isEmpty());
        verify(imageRepository).findByCaseEntryIdOrderBySortOrderAsc(caseId);
    }

    @Test
    void getImages_allowedForSecretaryWhenCaseNotDraft() {
        UUID actorId = UUID.randomUUID();
        User actor = User.builder()
                .id(actorId)
                .email("secretary@equity.com")
                .password("secret")
                .fullName("Secretary")
                .role(Role.SECRETARY)
                .build();
        User creator = User.builder()
                .id(UUID.randomUUID())
                .email("creator@equity.com")
                .password("secret")
                .fullName("Creator")
                .role(Role.COMMITTEE_MEMBER)
                .build();
        CaseEntry submittedCase = CaseEntry.builder()
                .id(caseId)
                .status(CaseStatus.UNDER_REVIEW)
                .createdBy(creator)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null)
        );
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(submittedCase));
        when(imageRepository.findByCaseEntryIdOrderBySortOrderAsc(caseId)).thenReturn(List.of());

        List<?> images = imageService.getImages(caseId);

        assertTrue(images.isEmpty());
        verify(imageRepository).findByCaseEntryIdOrderBySortOrderAsc(caseId);
    }

    @Test
    void updateCaption_trimsAndPersistsCaption() {
        CaseImage image = CaseImage.builder()
                .id(UUID.randomUUID())
                .caseEntry(caseRepository.findById(caseId).orElseThrow())
                .uploadedBy(userRepository.findById(userId).orElseThrow())
                .imageUrl("cases/key")
                .caption("old")
                .build();
        when(imageRepository.findById(image.getId())).thenReturn(Optional.of(image));
        when(imageRepository.save(any(CaseImage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        imageService.updateCaption(image.getId(), "  Updated caption  ");

        assertEquals("Updated caption", image.getCaption());
        verify(imageRepository).save(image);
    }

    @Test
    void updateCaption_emptyStringClearsCaption() {
        CaseImage image = CaseImage.builder()
                .id(UUID.randomUUID())
                .caseEntry(caseRepository.findById(caseId).orElseThrow())
                .uploadedBy(userRepository.findById(userId).orElseThrow())
                .imageUrl("cases/key")
                .caption("old")
                .build();
        when(imageRepository.findById(image.getId())).thenReturn(Optional.of(image));
        when(imageRepository.save(any(CaseImage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        imageService.updateCaption(image.getId(), "   ");

        assertEquals(null, image.getCaption());
        verify(imageRepository).save(image);
    }

    @Test
    void deleteImage_forbiddenForUnrelatedUser() {
        UUID actorId = UUID.randomUUID();
        User actor = User.builder()
                .id(actorId)
                .email("other@equity.com")
                .password("secret")
                .fullName("Other")
                .role(Role.COMMITTEE_MEMBER)
                .build();
        User uploader = User.builder()
                .id(UUID.randomUUID())
                .email("uploader@equity.com")
                .password("secret")
                .fullName("Uploader")
                .role(Role.COMMITTEE_MEMBER)
                .build();
        User caseCreator = User.builder()
                .id(UUID.randomUUID())
                .email("creator@equity.com")
                .password("secret")
                .fullName("Creator")
                .role(Role.COMMITTEE_MEMBER)
                .build();
        CaseEntry caseEntry = CaseEntry.builder()
                .id(caseId)
                .createdBy(caseCreator)
                .build();
        CaseImage image = CaseImage.builder()
                .id(UUID.randomUUID())
                .caseEntry(caseEntry)
                .uploadedBy(uploader)
                .imageUrl("cases/key")
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null)
        );
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(imageRepository.findById(image.getId())).thenReturn(Optional.of(image));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> imageService.deleteImage(image.getId()));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(s3Client, never()).deleteObject(any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class));
        verify(imageRepository, never()).delete(any(CaseImage.class));
    }

    @Test
    void updateCaption_forbiddenForUnrelatedUser() {
        UUID actorId = UUID.randomUUID();
        User actor = User.builder()
                .id(actorId)
                .email("other@equity.com")
                .password("secret")
                .fullName("Other")
                .role(Role.COMMITTEE_MEMBER)
                .build();
        User uploader = User.builder()
                .id(UUID.randomUUID())
                .email("uploader@equity.com")
                .password("secret")
                .fullName("Uploader")
                .role(Role.COMMITTEE_MEMBER)
                .build();
        User caseCreator = User.builder()
                .id(UUID.randomUUID())
                .email("creator@equity.com")
                .password("secret")
                .fullName("Creator")
                .role(Role.COMMITTEE_MEMBER)
                .build();
        CaseEntry caseEntry = CaseEntry.builder()
                .id(caseId)
                .createdBy(caseCreator)
                .build();
        CaseImage image = CaseImage.builder()
                .id(UUID.randomUUID())
                .caseEntry(caseEntry)
                .uploadedBy(uploader)
                .imageUrl("cases/key")
                .caption("old")
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null)
        );
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(imageRepository.findById(image.getId())).thenReturn(Optional.of(image));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> imageService.updateCaption(image.getId(), "new"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(imageRepository, never()).save(any(CaseImage.class));
    }
}
