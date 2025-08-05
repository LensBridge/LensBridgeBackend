package com.ibrasoft.lensbridge.repository;

import com.ibrasoft.lensbridge.model.upload.Upload;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UploadRepository extends MongoRepository<Upload, UUID> {
    Page<Upload> findByEventId(UUID eventId, Pageable pageable);

    Page<Upload> findByApprovedTrue(Pageable pageable);
    
    // Admin-specific query methods
    Page<Upload> findByApproved(boolean approved, Pageable pageable);
    
    Page<Upload> findByFeatured(boolean featured, Pageable pageable);
    
    Page<Upload> findByApprovedTrueAndFeaturedTrue(Pageable pageable);
    
    Page<Upload> findByApprovedTrueAndFeaturedFalse(Pageable pageable);
    
    Page<Upload> findByUploadedBy(UUID uploadedBy, Pageable pageable);
    
    // Count methods for user statistics
    long countByUploadedBy(UUID uploadedBy);
    
    long countByUploadedByAndApproved(UUID uploadedBy, boolean approved);
    
    long countByUploadedByAndFeatured(UUID uploadedBy, boolean featured);
}
