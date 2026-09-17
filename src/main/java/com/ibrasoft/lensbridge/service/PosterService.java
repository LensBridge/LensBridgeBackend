package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.board.request.CreatePosterRequest;
import com.ibrasoft.lensbridge.dto.board.request.UpdatePosterRequest;
import com.ibrasoft.lensbridge.dto.upload.response.ErrorResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.handler.BoardStreamHandler;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Poster;
import java.time.Instant;
import com.ibrasoft.lensbridge.util.Patch;
import com.ibrasoft.lensbridge.repository.sql.PosterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosterService {

    private final PosterRepository posterRepository;
    private final R2StorageService r2StorageService;
    private final BoardStreamHandler boardStream;

    @Value("${cloudflare.r2.public-url}")
    private String publicUrl;

    /**
     * Get all posters, sorted by startDate descending (newest first).
     */
    public List<Poster> getAllPosters() {
        return posterRepository.findAllByOrderByStartTimeDesc();
    }

    /**
     * Get a poster by ID.
     */
    public Poster getPosterById(UUID posterId) {
        Poster poster = posterRepository.findById(posterId)
                .orElseThrow(() -> new ApiResponseException(
                        HttpStatus.NOT_FOUND,
                        ErrorResponse.of("Poster not found with id: " + posterId)));
        return poster;
    }

    /**
     * Get all posters for a specific board location.
     * Returns posters that match the board's audience or BOTH.
     */
    public List<Poster> getPostersForAudience(Audience audience) {
        return posterRepository.findByAudienceOrBoth(audience);
    }

    /**
     * Get active posters for a specific audience as FrameDefinitions.
     * Returns posters that are currently active (startDate <= today < endDate)
     * and match the audience or BOTH.
     */
    public List<Poster> getActivePosterFramesForAudience(Audience audience) {
        return posterRepository.findActivePostersForAudienceAt(Instant.now(), audience);
    }

    /**
     * Get all active posters (currently within their viewing window).
     */
    public List<Poster> getActivePosters() {
        return posterRepository.findActivePostersAt(Instant.now());
    }

    /**
     * Create a new poster with an uploaded image.
     */
    public Poster createPoster(CreatePosterRequest request) {
        validateDates(request.getStartTime(), request.getEndTime());
        validateImageFile(request.getImageFile());

        // Upload the image to R2
        String objectKey;
        try {
            String filename = generatePosterFilename(request.getImageFile().getOriginalFilename());
            objectKey = r2StorageService.uploadImage(filename, request.getImageFile());
            log.info("Uploaded poster image to R2: {}", objectKey);
        } catch (IOException e) {
            log.error("Failed to upload poster image", e);
            throw new ApiResponseException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorResponse.of("Failed to upload poster image: " + e.getMessage()));
        }

        Poster poster = Poster.builder()
                .title(request.getTitle())
                .image(publicUrl + "/" + objectKey)
                .duration(request.getDuration())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .audience(request.getAudience())
                .signupUrl(request.getSignupUrl())
                .build();

        poster = posterRepository.save(poster);
        log.info("Created poster: id={}, title={}", poster.getId(), poster.getTitle());
        boardStream.contentChanged("posters");

        return poster;
    }

    /**
     * Update an existing poster's metadata.
     */
    public Poster updatePoster(UUID posterId, UpdatePosterRequest request) {
        Poster poster = posterRepository.findById(posterId)
                .orElseThrow(() -> new ApiResponseException(
                        HttpStatus.NOT_FOUND,
                        ErrorResponse.of("Poster not found with id: " + posterId)));

        // Update only non-null fields
        Patch.apply(request.getTitle(), poster::setTitle);
        Patch.apply(request.getDuration(), poster::setDuration);
        Patch.apply(request.getStartTime(), poster::setStartTime);
        Patch.apply(request.getEndTime(), poster::setEndTime);
        Patch.apply(request.getAudience(), poster::setAudience);
        // An empty string clears the link, so the QR panel can be removed from a
        // poster without deleting and re-uploading it. Not Patch.apply: the lambda
        // would capture `poster`, which is reassigned by save() below.
        if (request.getSignupUrl() != null) {
            String signupUrl = request.getSignupUrl().trim();
            poster.setSignupUrl(signupUrl.isEmpty() ? null : signupUrl);
        }

        validateDates(poster.getStartTime(), poster.getEndTime());

        poster = posterRepository.save(poster);
        log.info("Updated poster: id={}", posterId);
        boardStream.contentChanged("posters");

        return poster;
    }

    /**
     * Update a poster's image.
     */
    public Poster updatePosterImage(UUID posterId, MultipartFile imageFile) {
        Poster poster = posterRepository.findById(posterId)
                .orElseThrow(() -> new ApiResponseException(
                        HttpStatus.NOT_FOUND,
                        ErrorResponse.of("Poster not found with id: " + posterId)));

        validateImageFile(imageFile);

        // Delete old image from R2 if exists
        if (poster.getImage() != null && !poster.getImage().isBlank()) {
            try {
                r2StorageService.deleteObject(poster.getImage());
                log.info("Deleted old poster image: {}", poster.getImage());
            } catch (Exception e) {
                log.warn("Failed to delete old poster image: {}", poster.getImage(), e);
            }
        }

        // Upload new image
        String objectKey;
        try {
            String filename = generatePosterFilename(imageFile.getOriginalFilename());
            objectKey = r2StorageService.uploadImage(filename, imageFile);
            log.info("Uploaded new poster image to R2: {}", objectKey);
        } catch (IOException e) {
            log.error("Failed to upload poster image", e);
            throw new ApiResponseException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorResponse.of("Failed to upload poster image: " + e.getMessage()));
        }

        poster.setImage(publicUrl + "/" + objectKey);
        poster = posterRepository.save(poster);
        log.info("Updated poster image: id={}", posterId);
        boardStream.contentChanged("posters");

        return poster;
    }

    /**
     * Delete a poster and its associated image.
     */
    public void deletePoster(UUID posterId) {
        Poster poster = posterRepository.findById(posterId)
                .orElseThrow(() -> new ApiResponseException(
                        HttpStatus.NOT_FOUND,
                        ErrorResponse.of("Poster not found with id: " + posterId)));

        // Delete image from R2 if exists
        if (poster.getImage() != null && !poster.getImage().isBlank()) {
            try {
                r2StorageService.deleteObject(poster.getImage());
                log.info("Deleted poster image: {}", poster.getImage());
            } catch (Exception e) {
                log.warn("Failed to delete poster image: {}", poster.getImage(), e);
            }
        }

        posterRepository.delete(poster);
        log.info("Deleted poster: id={}", posterId);
        boardStream.contentChanged("posters");
    }

    // ==================== Helper Methods ====================

    private String generatePosterFilename(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return "poster-" + UUID.randomUUID() + extension;
    }

    private void validateDates(Instant startDate, Instant endDate) {
        if (startDate != null && endDate != null && !endDate.isAfter(startDate)) {
            throw new ApiResponseException(
                    HttpStatus.BAD_REQUEST,
                    ErrorResponse.of("End date must be after start date"));
        }
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiResponseException(
                    HttpStatus.BAD_REQUEST,
                    ErrorResponse.of("Image file is required"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ApiResponseException(
                    HttpStatus.BAD_REQUEST,
                    ErrorResponse.of("File must be an image"));
        }

        // Max 10MB for poster images
        long maxSize = 10 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new ApiResponseException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    ErrorResponse.of("Poster image must be less than 10MB"));
        }
    }
}
