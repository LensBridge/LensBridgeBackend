package com.ibrasoft.lensbridge.repository.upload;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.upload.MediaEvent;
import com.ibrasoft.lensbridge.model.upload.Upload;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface UploadRepository extends JpaRepository<Upload, UUID> {
    /**
     * Recall that deletedAt = null => not deleted. Therefore, this method will only return uploads that are not marked as deleted.
     */
    Page<Upload> findByDeletedAtIsNull(Pageable pageable);

    Page<Upload> findByApprovedTrueAndDeletedAtIsNull(Pageable pageable);

    Page<Upload> findByMediaEventAndApprovedTrueAndDeletedAtIsNull(MediaEvent mediaEvent, Pageable pageable);

    Page<Upload> findByMediaEventAndDeletedAtIsNull(MediaEvent mediaEvent, Pageable pageable);

    Page<Upload> findByApprovedAndDeletedAtIsNull(boolean approved, Pageable pageable);

    Page<Upload> findByFeaturedAndDeletedAtIsNull(boolean featured, Pageable pageable);

    Page<Upload> findByUploadedByAndDeletedAtIsNull(User uploadedBy, Pageable pageable);

    long countByUploadedByAndDeletedAtIsNull(User uploadedBy);

    long countByUploadedByAndApprovedAndDeletedAtIsNull(User uploadedBy, boolean approved);

    long countByUploadedByAndFeaturedAndDeletedAtIsNull(User uploadedBy, boolean featured);

    long countByUploadedByAndCreatedDateBetweenAndDeletedAtIsNull(User uploadedBy, Instant start, Instant end);
}
