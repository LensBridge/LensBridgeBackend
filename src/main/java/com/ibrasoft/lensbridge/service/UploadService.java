package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.upload.response.AdminUploadDto;
import com.ibrasoft.lensbridge.dto.upload.response.UploadDto;
import com.ibrasoft.lensbridge.dto.auth.response.UserStatsResponse;
import com.ibrasoft.lensbridge.exception.FileProcessingException;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.minbar.BoardEvent;
import com.ibrasoft.lensbridge.model.upload.Upload;
import com.ibrasoft.lensbridge.model.upload.UploadType;
import com.ibrasoft.lensbridge.repository.sql.BoardEventRepository;
import com.ibrasoft.lensbridge.repository.upload.UploadRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadService {

    private final UploadRepository uploadRepository;
    private final UserService userService;
    private final BoardEventRepository boardEventRepository;
    private final R2StorageService r2StorageService;

    @Value("${uploads.default-approved:false}")
    private boolean defaultApproved;

    @Value("${uploads.default-featured:false}")
    private boolean defaultFeatured;

    // ── Entity creation ───────────────────────────────────────────────────────

    public Upload createUpload(String objectKey, String fileName,
            UUID eventId, String description, String instagramHandle,
            boolean anon, UUID uploadedBy) {
        try {
            User user = userService.findById(uploadedBy)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            BoardEvent boardEvent = boardEventRepository.findById(eventId)
                    .orElseThrow(() -> new IllegalArgumentException("Event not found"));

            Upload upload = new Upload();
            upload.setFileName(fileName);
            upload.setFileUrl(objectKey);
            upload.setThumbnailUrl(null);
            upload.setUploadDescription(description);
            upload.setInstagramHandle(instagramHandle);
            upload.setUploadedBy(user);
            upload.setBoardEvent(boardEvent);
            upload.setCreatedDate(Instant.now());
            upload.setApproved(defaultApproved);
            upload.setFeatured(defaultFeatured);
            upload.setAnon(anon);
            upload.setContentType(UploadType.IMAGE);

            uploadRepository.save(upload);
            log.info("Created upload record: {} for object: {}", upload.getUuid(), objectKey);
            return upload;
        } catch (Exception e) {
            log.error("Failed to create upload record for object: {}", objectKey, e);
            throw new FileProcessingException("Failed to create upload record");
        }
    }

    // ── Status mutations ──────────────────────────────────────────────────────

    public void approveUpload(UUID uploadId) {
        Upload upload = findRequiredById(uploadId);
        upload.setApproved(true);
        uploadRepository.save(upload);
        log.info("Upload {} approved", uploadId);
    }

    public void unapproveUpload(UUID uploadId) {
        Upload upload = findRequiredById(uploadId);
        upload.setApproved(false);
        uploadRepository.save(upload);
        log.info("Upload {} unapproved", uploadId);
    }

    public void featureUpload(UUID uploadId) {
        Upload upload = findRequiredById(uploadId);
        upload.setFeatured(true);
        uploadRepository.save(upload);
        log.info("Upload {} featured", uploadId);
    }

    public void unfeatureUpload(UUID uploadId) {
        Upload upload = findRequiredById(uploadId);
        upload.setFeatured(false);
        uploadRepository.save(upload);
        log.info("Upload {} unfeatured", uploadId);
    }

    // ── Deletion ──────────────────────────────────────────────────────────────

    public void deleteUpload(UUID id) {
        Optional<Upload> uploadOpt = uploadRepository.findById(id);
        uploadOpt.ifPresent(upload -> {
            deleteFromStorage(upload);
            upload.setDeletedAt(Instant.now());
            uploadRepository.save(upload);
        });
        if (uploadOpt.isEmpty()) {
            log.warn("Attempted to delete non-existent upload: {}", id);
            throw new IllegalArgumentException("Upload not found");
        }
    }

    public void deleteUserUpload(UUID uploadId, UUID userId) {
        log.info("User {} deleting upload {}", userId, uploadId);

        Upload upload = uploadRepository.findById(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("Upload not found"));

        if (upload.getUploadedBy() == null || !upload.getUploadedBy().getId().equals(userId)) {
            throw new SecurityException("You can only delete your own uploads");
        }

        deleteFromStorage(upload);
        upload.setDeletedAt(Instant.now());
        userService.findById(userId).ifPresent(upload::setDeletedBy);
        uploadRepository.save(upload);
        log.info("Upload {} deleted by user {}", uploadId, userId);
    }

    private void deleteFromStorage(Upload upload) {
        try {
            String key = r2StorageService.extractObjectKey(upload.getFileUrl());
            if (key != null) r2StorageService.deleteObject(key);
            String thumbKey = upload.getThumbnailUrl();
            if (thumbKey != null && !thumbKey.isBlank()) r2StorageService.deleteObject(thumbKey);
        } catch (Exception e) {
            log.warn("Failed to delete R2 objects for upload {}: {}", upload.getUuid(), e.getMessage());
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public Optional<Upload> getUploadById(UUID id) {
        return uploadRepository.findById(id);
    }

    /**
     * Reads a single upload as the given viewer would be allowed to see it.
     * <p>
     * A viewer holding {@code media:upload:read} sees any live upload. Everyone else sees an
     * upload only when it is already approved or they uploaded it themselves; anything else comes
     * back empty so the caller answers 404 rather than 403 — a 403 would confirm that the id
     * exists, which is exactly what an enumeration attempt is fishing for.
     *
     * @param viewerId   id of the authenticated caller, may be {@code null} for no ownership claim
     * @param canReadAll whether the caller holds {@code media:upload:read}
     */
    public Optional<UploadDto> getUploadByIdAsDto(UUID id, UUID viewerId, boolean canReadAll) {
        return uploadRepository.findById(id)
                .filter(upload -> upload.getDeletedAt() == null)
                .filter(upload -> canReadAll || isVisibleTo(upload, viewerId))
                .map(this::toUploadDto);
    }

    public Page<Upload> getAllUploads(Pageable pageable) {
        return uploadRepository.findByDeletedAtIsNull(pageable);
    }

    public Page<Upload> getUploadsByEvent(UUID eventId, Pageable pageable) {
        BoardEvent boardEvent = boardEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        return uploadRepository.findByBoardEventAndDeletedAtIsNull(boardEvent, pageable);
    }

    /**
     * Pages an event's uploads as the given viewer would be allowed to see them: everything for a
     * holder of {@code media:upload:read}, otherwise only approved uploads plus the viewer's own.
     *
     * @param viewerId   id of the authenticated caller, may be {@code null} for no ownership claim
     * @param canReadAll whether the caller holds {@code media:upload:read}
     */
    public Page<UploadDto> getUploadsByEventAsDto(UUID eventId, Pageable pageable, UUID viewerId, boolean canReadAll) {
        BoardEvent boardEvent = boardEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        Page<Upload> uploads = canReadAll
                ? uploadRepository.findByBoardEventAndDeletedAtIsNull(boardEvent, pageable)
                : uploadRepository.findVisibleByBoardEvent(boardEvent, viewerId, pageable);
        return uploads.map(this::toUploadDto);
    }

    public Page<UploadDto> getUserUploads(UUID userId, Pageable pageable) {
        User user = userService.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return uploadRepository.findByUploadedByAndDeletedAtIsNull(user, pageable).map(this::toUploadDto);
    }

    public Page<AdminUploadDto> getAllUploadsForAdmin(Pageable pageable) {
        return uploadRepository.findByDeletedAtIsNull(pageable).map(this::toAdminDto);
    }

    public Page<AdminUploadDto> getUploadsByApprovalStatus(boolean approved, Pageable pageable) {
        return uploadRepository.findByApprovedAndDeletedAtIsNull(approved, pageable).map(this::toAdminDto);
    }

    public Page<AdminUploadDto> getUploadsByFeaturedStatus(boolean featured, Pageable pageable) {
        return uploadRepository.findByFeaturedAndDeletedAtIsNull(featured, pageable).map(this::toAdminDto);
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    public long countUploadsToday(UUID userId) {
        LocalDate today = LocalDate.now();
        Instant startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endOfDay = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        User user = userService.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return uploadRepository.countByUploadedByAndCreatedDateBetweenAndDeletedAtIsNull(user, startOfDay, endOfDay);
    }

    public boolean hasReachedDailyLimit(UUID userId, int dailyLimit) {
        return countUploadsToday(userId) >= dailyLimit;
    }

    public UserStatsResponse getUserStats(UUID userId) {
        User user = userService.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        long total = uploadRepository.countByUploadedByAndDeletedAtIsNull(user);
        long approved = uploadRepository.countByUploadedByAndApprovedAndDeletedAtIsNull(user, true);
        long featured = uploadRepository.countByUploadedByAndFeaturedAndDeletedAtIsNull(user, true);
        return new UserStatsResponse((int) total, (int) approved, (int) featured, (int) (total - approved));
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** Approved content is public to signed-in users; unapproved content is the uploader's alone. */
    private boolean isVisibleTo(Upload upload, UUID viewerId) {
        return upload.isApproved() || isOwnedBy(upload, viewerId);
    }

    private boolean isOwnedBy(Upload upload, UUID viewerId) {
        return viewerId != null
                && upload.getUploadedBy() != null
                && viewerId.equals(upload.getUploadedBy().getId());
    }

    private Upload findRequiredById(UUID id) {
        return uploadRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Upload not found: " + id));
    }

    // TODO: Consolidate this in UploadDTO class as from(Upload)
    private UploadDto toUploadDto(Upload upload) {
        return new UploadDto(
                upload.getUuid(),
                upload.getFileName(),
                upload.getFileUrl(),
                upload.getThumbnailUrl(),
                upload.getUploadDescription(),
                upload.getInstagramHandle(),
                upload.getUploadedBy() != null ? upload.getUploadedBy().getId() : null,
                upload.getBoardEvent() != null ? upload.getBoardEvent().getId() : null,
                upload.getBoardEvent() != null ? upload.getBoardEvent().getName() : null,
                upload.getCreatedDate(),
                upload.isApproved(),
                upload.isFeatured(),
                upload.isAnon(),
                upload.getContentType());
    }

    private AdminUploadDto toAdminDto(Upload upload) {
        AdminUploadDto dto = new AdminUploadDto();
        dto.setUuid(upload.getUuid());
        dto.setFileName(upload.getFileName());
        dto.setFileUrl(upload.getFileUrl());
        dto.setUploadDescription(upload.getUploadDescription());
        dto.setInstagramHandle(upload.getInstagramHandle());
        dto.setUploadedBy(upload.getUploadedBy() != null ? upload.getUploadedBy().getId() : null);
        dto.setEventId(upload.getBoardEvent() != null ? upload.getBoardEvent().getId() : null);
        dto.setCreatedDate(upload.getCreatedDate());
        dto.setApproved(upload.isApproved());
        dto.setFeatured(upload.isFeatured());
        dto.setAnon(upload.isAnon());
        dto.setContentType(upload.getContentType());

        try {
            String key = r2StorageService.extractObjectKey(upload.getFileUrl());
            dto.setSecureUrl(r2StorageService.getSecureUrl(key, upload.isApproved(), true));
            String thumbKey = upload.getThumbnailUrl();
            String thumbUrl = (thumbKey != null && !thumbKey.isBlank())
                    ? r2StorageService.getSecureThumbnailUrl(thumbKey, upload.isApproved(), true)
                    : dto.getSecureUrl();
            dto.setThumbnailUrl(thumbUrl);
        } catch (Exception e) {
            log.error("Failed to generate secure URLs for upload {}: {}", upload.getUuid(), e.getMessage());
            dto.setSecureUrl(null);
            dto.setThumbnailUrl(null);
        }

        if (upload.getUploadedBy() != null) {
            User u = upload.getUploadedBy();
            dto.setUploaderFirstName(u.getFirstName());
            dto.setUploaderLastName(u.getLastName());
            dto.setUploaderEmail(u.getEmail());
        }

        if (upload.getBoardEvent() != null) {
            dto.setEventName(upload.getBoardEvent().getName());
        }

        return dto;
    }
}
