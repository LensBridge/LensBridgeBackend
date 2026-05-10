package com.ibrasoft.lensbridge.repository.upload;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ibrasoft.lensbridge.model.upload.Upload;

import java.util.UUID;

@Repository
public interface UploadRepository extends JpaRepository<Upload, UUID> {
    /**
     * Recall that deletedAt = null => not deleted. Therefore, this method will only return uploads that are not marked as deleted.
     */
    Page<Upload> findByDeletedAtIsNull(Pageable pageable);
}
