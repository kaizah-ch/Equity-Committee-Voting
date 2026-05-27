package com.equitycommittee.voting.api.controller;

import com.equitycommittee.voting.api.dto.images.ImageResponse;
import com.equitycommittee.voting.api.dto.images.UpdateImageCaptionRequest;
import com.equitycommittee.voting.service.ImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    @PostMapping("/api/cases/{caseId}/images")
    public ResponseEntity<ImageResponse> uploadImage(
            @PathVariable UUID caseId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String caption) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(imageService.uploadImage(caseId, file, caption));
    }

    @GetMapping("/api/cases/{caseId}/images")
    public ResponseEntity<List<ImageResponse>> getImages(@PathVariable UUID caseId) {
        return ResponseEntity.ok(imageService.getImages(caseId));
    }

    @DeleteMapping("/api/images/{imageId}")
    public ResponseEntity<Void> deleteImage(@PathVariable UUID imageId) {
        imageService.deleteImage(imageId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/images/{imageId}/caption")
    public ResponseEntity<ImageResponse> updateCaption(
            @PathVariable UUID imageId,
            @Valid @RequestBody UpdateImageCaptionRequest request) {
        return ResponseEntity.ok(imageService.updateCaption(imageId, request.caption()));
    }
}
