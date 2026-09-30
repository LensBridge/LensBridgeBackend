package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.upload.response.AdminUploadDto;
import com.ibrasoft.lensbridge.dto.upload.response.UploadDto;
import com.ibrasoft.lensbridge.dto.auth.response.UserStatsResponse;
import com.ibrasoft.lensbridge.exception.FileProcessingException;
import com.ibrasoft.lensbridge.model.auth.Permission;
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
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    public Optional<UploadDto> getUploadByIdAsDto(UUID id) {
        UploadViewer viewer = currentViewer();
        return uploadRepository.findById(id).map(upload -> toUploadDto(upload, viewer));
    }

    public Page<Upload> getAllUploads(Pageable pageable) {
        return uploadRepository.findByDeletedAtIsNull(pageable);
    }

    public Page<Upload> getUploadsByEvent(UUID eventId, Pageable pageable) {
        BoardEvent boardEvent = boardEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        return uploadRepository.findByBoardEventAndDeletedAtIsNull(boardEvent, pageable);
    }

    public Page<UploadDto> getUploadsByEventAsDto(UUID eventId, Pageable pageable) {
        BoardEvent boardEvent = boardEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        UploadViewer viewer = currentViewer();
        return uploadRepository.findByBoardEventAndDeletedAtIsNull(boardEvent, pageable)
                .map(upload -> toUploadDto(upload, viewer));
    }

    public Page<UploadDto> getUserUploads(UUID userId, Pageable pageable) {
        User user = userService.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        UploadViewer viewer = currentViewer();
        return uploadRepository.findByUploadedByAndDeletedAtIsNull(user, pageable)
                .map(upload -> toUploadDto(upload, viewer));
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

    private Upload findRequiredById(UUID id) {
        return uploadRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Upload not found: " + id));
    }

    /**
     * Who is asking for a {@link UploadDto}, and what they are allowed to see.
     * <p>
     * Resolved once per query rather than once per row: a page of 50 uploads should cost one
     * principal lookup, not 50.
     *
     * @param userId            the caller's account id, or {@code null} for an unauthenticated caller
     * @param canReadAllUploads whether the caller holds {@code media:upload:read}
     */
    record UploadViewer(UUID userId, boolean canReadAllUploads) {

        static UploadViewer anonymous() {
            return new UploadViewer(null, false);
        }

        /** True when this viewer is the account that submitted the upload. */
        boolean isUploader(Upload upload) {
            return userId != null
                    && upload.getUploadedBy() != null
                    && userId.equals(upload.getUploadedBy().getId());
        }

        /**
         * Whether the uploader's identity may be disclosed on an anonymous upload, and whether
         * unapproved bytes may be presigned. Moderators need both to do review; the uploader
         * needs both to see their own pending submission.
         */
        boolean isPrivilegedFor(Upload upload) {
            return canReadAllUploads || isUploader(upload);
        }
    }

    /**
     * Resolve the caller from the security context. Never throws: an unresolvable principal
     * degrades to the least-privileged viewer rather than failing the request.
     */
    UploadViewer currentViewer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return UploadViewer.anonymous();
        }

        boolean canReadAll = authentication.getAuthorities().stream()
                .anyMatch(a -> Permission.Authority.MEDIA_UPLOAD_READ.equals(a.getAuthority()));

        UUID userId = userService.findByEmail(authentication.getName())
                .map(User::getId)
                .orElse(null);

        return new UploadViewer(userId, canReadAll);
    }

    // TODO: Consolidate this in UploadDTO class as from(Upload)
    UploadDto toUploadDto(Upload upload, UploadViewer viewer) {
        boolean privileged = viewer.isPrivilegedFor(upload);

        UploadDto dto = new UploadDto();
        dto.setUuid(upload.getUuid());
        dto.setFileName(upload.getFileName());
        dto.setUploadDescription(upload.getUploadDescription());
        dto.setEventId(upload.getBoardEvent() != null ? upload.getBoardEvent().getId() : null);
        dto.setEventName(upload.getBoardEvent() != null ? upload.getBoardEvent().getName() : null);
        dto.setCreatedDate(upload.getCreatedDate());
        dto.setApproved(upload.isApproved());
        dto.setFeatured(upload.isFeatured());
        dto.setAnon(upload.isAnon());
        dto.setContentType(upload.getContentType());

        // Anonymity is a promise made to the uploader at submit time. Only the uploader
        // themselves and moderators (media:upload:read) get to see behind it.
        boolean masked = upload.isAnon() && !privileged;
        dto.setUploadedBy(masked || upload.getUploadedBy() == null
                ? null
                : upload.getUploadedBy().getId());
        dto.setInstagramHandle(masked ? null : upload.getInstagramHandle());

        applySecureUrls(dto, upload, privileged);
        return dto;
    }

    /**
     * Replace the stored R2 object keys with scoped, expiring presigned URLs.
     * <p>
     * The keys themselves never leave the server: the bucket is fronted by a public custom
     * domain, so handing out a key is handing out the media. {@code getSecureUrl} is also where
     * the approval gate lives -- it refuses unapproved content to unprivileged callers. A refusal
     * (or any storage failure) nulls the URLs instead of failing the whole listing.
     */
    private void applySecureUrls(UploadDto dto, Upload upload, boolean privileged) {
        try {
            String fileKey = r2StorageService.extractObjectKey(upload.getFileUrl());
            if (fileKey == null) {
                return;
            }

            String fileUrl = r2StorageService.getSecureUrl(fileKey, upload.isApproved(), privileged);

            String thumbKey = r2StorageService.extractObjectKey(upload.getThumbnailUrl());
            String thumbUrl = thumbKey != null
                    ? r2StorageService.getSecureThumbnailUrl(thumbKey, upload.isApproved(), privileged)
                    : fileUrl;

            dto.setFileUrl(fileUrl);
            dto.setThumbnailUrl(thumbUrl);
            dto.setUrlExpiresAt(Instant.now().plus(r2StorageService.getDownloadUrlExpiration()));
        } catch (SecurityException e) {
            log.debug("Withholding media URLs for upload {}: {}", upload.getUuid(), e.getMessage());
        } catch (Exception e) {
            log.error("Failed to generate secure URLs for upload {}: {}", upload.getUuid(), e.getMessage());
        }
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
