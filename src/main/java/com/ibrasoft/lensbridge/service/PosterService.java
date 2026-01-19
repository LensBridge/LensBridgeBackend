package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.request.CreatePosterRequest;
import com.ibrasoft.lensbridge.dto.request.UpdatePosterRequest;
import com.ibrasoft.lensbridge.dto.response.ErrorResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.BoardLocation;
import com.ibrasoft.lensbridge.model.board.Poster;
import com.ibrasoft.lensbridge.repository.PosterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosterService {

    private final PosterRepository posterRepository;
    private final R2StorageService r2StorageService;

    @Value("${cloudflare.r2.public-url}")
    private String publicUrl;

    private static final Sort SORT_BY_START_DATE_DESC = Sort.by(Sort.Direction.DESC, "startDate");

    /**
     * Get all posters, sorted by startDate descending (newest first).
     */
    public List<Poster> getAllPosters() {
        return posterRepository.findAllByOrderByStartDateDesc();
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
    public List<Poster> getPostersForBoard(BoardLocation boardLocation) {
        Audience audience = boardLocationToAudience(boardLocation);
        return posterRepository.findByAudienceOrBoth(audience, SORT_BY_START_DATE_DESC);
    }

    /**
     * Get active posters for a specific board location as FrameDefinitions.
     * Returns posters that are currently active (startDate <= today < endDate)
     * and match the board's audience or BOTH.
     */
    public List<Poster> getActivePosterFramesForBoard(BoardLocation boardLocation) {
        Audience audience = boardLocationToAudience(boardLocation);
        LocalDate today = LocalDate.now();
        
        return posterRepository.findActivePostersForAudienceAt(today, audience, SORT_BY_START_DATE_DESC);
    }

    /**
     * Get all active posters (currently within their viewing window).
     */
    public List<Poster> getActivePosters() {
        LocalDate today = LocalDate.now();
        return posterRepository.findActivePostersAt(today);
    }

    /**
     * Create a new poster with an uploaded image.
     */
    public Poster createPoster(CreatePosterRequest request, MultipartFile imageFile) {
        validateDates(request.getStartDate(), request.getEndDate());
        validateImageFile(imageFile);

        // Upload the image to R2
        String objectKey;
        try {
            String filename = generatePosterFilename(imageFile.getOriginalFilename());
            objectKey = r2StorageService.uploadImage(imageFile.getBytes(), filename);
            log.info("Uploaded poster image to R2: {}", objectKey);
        } catch (IOException e) {
            log.error("Failed to upload poster image", e);
            throw new ApiResponseException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorResponse.of("Failed to upload poster image: " + e.getMessage()));
        }

        Poster poster = Poster.builder()
                .id(UUID.randomUUID())
                .title(request.getTitle())
                .image(publicUrl + "/" + objectKey)
                .duration(request.getDuration())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .audience(request.getAudience())
                .build();

        poster = posterRepository.save(poster);
        log.info("Created poster: id={}, title={}", poster.getId(), poster.getTitle());

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
        if (request.getTitle() != null) {
            poster.setTitle(request.getTitle());
        }
        if (request.getDuration() != null) {
            poster.setDuration(request.getDuration());
        }
        if (request.getStartDate() != null) {
            poster.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            poster.setEndDate(request.getEndDate());
        }
        if (request.getAudience() != null) {
            poster.setAudience(request.getAudience());
        }

        // Validate dates after update
        validateDates(poster.getStartDate(), poster.getEndDate());

        poster = posterRepository.save(poster);
        log.info("Updated poster: id={}", posterId);

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
            objectKey = r2StorageService.uploadImage(imageFile.getBytes(), filename);
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
    }

    // ==================== Musallah Board Methods ====================

    /**
     * Get active posters as FrameDefinitions for display on the musallah board.
     * Returns only posters that are currently active and match the board's audience.
     * Sorted by startDate descending (newest first).
     */
    public List<com.ibrasoft.lensbridge.model.board.frames.FrameDefinition> getActivePosterFrameDefinitions(BoardLocation boardLocation) {
        Audience audience = boardLocationToAudience(boardLocation);
        LocalDate today = LocalDate.now();
        
        return posterRepository.findActivePostersForAudienceAt(today, audience, SORT_BY_START_DATE_DESC)
                .stream()
                .map(this::toFrameDefinition)
                .collect(Collectors.toList());
    }

    /**
     * Convert a Poster to a FrameDefinition for the musallah board.
     */
    private com.ibrasoft.lensbridge.model.board.frames.FrameDefinition toFrameDefinition(Poster poster) {
        
        com.ibrasoft.lensbridge.model.board.frames.PosterFrameConfig config = 
            com.ibrasoft.lensbridge.model.board.frames.PosterFrameConfig.builder()
                .posterUrl(poster.getImage())
                .title(poster.getTitle())
                .build();
        
        return com.ibrasoft.lensbridge.model.board.frames.FrameDefinition.builder()
                .frameType(com.ibrasoft.lensbridge.model.board.frames.FrameType.POSTER)
                .durationInSeconds(poster.getDuration())
                .frameConfig(config)
                .build();
    }

    // ==================== Helper Methods ====================

    private Audience boardLocationToAudience(BoardLocation boardLocation) {
        return switch (boardLocation) {
            case BROTHERS_MUSALLAH -> Audience.BROTHERS;
            case SISTERS_MUSALLAH -> Audience.SISTERS;
        };
    }

    private String generatePosterFilename(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return "poster-" + UUID.randomUUID() + extension;
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
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
