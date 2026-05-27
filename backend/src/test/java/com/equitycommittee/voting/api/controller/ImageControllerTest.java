package com.equitycommittee.voting.api.controller;

import com.equitycommittee.voting.api.dto.images.ImageResponse;
import com.equitycommittee.voting.api.dto.images.UpdateImageCaptionRequest;
import com.equitycommittee.voting.service.ImageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageControllerTest {

    @Mock
    private ImageService imageService;

    @InjectMocks
    private ImageController imageController;

    @Test
    void uploadImage_returnsCreatedAndDelegatesService() throws IOException {
        UUID caseId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "collateral.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        String caption = "Collateral side view";
        ImageResponse responseBody = sampleImageResponse(caseId, caption);

        when(imageService.uploadImage(caseId, file, caption)).thenReturn(responseBody);

        var response = imageController.uploadImage(caseId, file, caption);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(responseBody, response.getBody());
        verify(imageService).uploadImage(caseId, file, caption);
    }

    @Test
    void getImages_returnsOkAndDelegatesService() {
        UUID caseId = UUID.randomUUID();
        List<ImageResponse> images = List.of(
                sampleImageResponse(caseId, "Front view"),
                sampleImageResponse(caseId, "Rear view")
        );
        when(imageService.getImages(caseId)).thenReturn(images);

        var response = imageController.getImages(caseId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(images, response.getBody());
        verify(imageService).getImages(caseId);
    }

    @Test
    void deleteImage_returnsNoContentAndDelegatesService() {
        UUID imageId = UUID.randomUUID();

        var response = imageController.deleteImage(imageId);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(imageService).deleteImage(imageId);
    }

    @Test
    void updateCaption_returnsOkAndDelegatesService() {
        UUID imageId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        UpdateImageCaptionRequest request = new UpdateImageCaptionRequest("Updated caption");
        ImageResponse responseBody = sampleImageResponse(caseId, "Updated caption");
        when(imageService.updateCaption(imageId, request.caption())).thenReturn(responseBody);

        var response = imageController.updateCaption(imageId, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(responseBody, response.getBody());
        verify(imageService).updateCaption(imageId, request.caption());
    }

    private ImageResponse sampleImageResponse(UUID caseId, String caption) {
        return ImageResponse.builder()
                .id(UUID.randomUUID())
                .caseId(caseId)
                .uploadedById(UUID.randomUUID())
                .imageUrl("https://example.com/image.jpg")
                .caption(caption)
                .sortOrder(0)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
