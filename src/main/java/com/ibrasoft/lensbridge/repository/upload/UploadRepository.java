package com.ibrasoft.lensbridge.repository.upload;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.minbar.BoardEvent;
import com.ibrasoft.lensbridge.model.upload.Upload;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface UploadRepository extends JpaRepository<Upload, UUID> {

    Upload findByUuidAndDeletedAtIsNull(UUID uuid);

    Page<Upload> findByDeletedAtIsNull(Pageable pageable);

    Page<Upload> findByApprovedTrueAndDeletedAtIsNull(Pageable pageable);
    
    Page<Upload> findByBoardEventAndDeletedAtIsNull(BoardEvent boardEvent, Pageable pageable);

    /**
     * Uploads on an event that an unprivileged caller is allowed to see: everything already
     * approved, plus the caller's own submissions regardless of moderation state. Callers holding
     * {@code media:upload:read} bypass this and use {@link #findByBoardEventAndDeletedAtIsNull}.
     */
    @Query("SELECT u FROM Upload u WHERE u.boardEvent = :boardEvent AND u.deletedAt IS NULL "
            + "AND (u.approved = true OR u.uploadedBy.id = :viewerId)")
    Page<Upload> findVisibleByBoardEvent(@Param("boardEvent") BoardEvent boardEvent,
                                         @Param("viewerId") UUID viewerId,
                                         Pageable pageable);

    Page<Upload> findByApprovedAndDeletedAtIsNull(boolean approved, Pageable pageable);

    Page<Upload> findByFeaturedAndDeletedAtIsNull(boolean featured, Pageable pageable);

    Page<Upload> findByUploadedByAndDeletedAtIsNull(User uploadedBy, Pageable pageable);

    long countByUploadedByAndDeletedAtIsNull(User uploadedBy);

    long countByUploadedByAndApprovedAndDeletedAtIsNull(User uploadedBy, boolean approved);

    long countByUploadedByAndFeaturedAndDeletedAtIsNull(User uploadedBy, boolean featured);

    long countByUploadedByAndCreatedDateBetweenAndDeletedAtIsNull(User uploadedBy, Instant start, Instant end);
}
