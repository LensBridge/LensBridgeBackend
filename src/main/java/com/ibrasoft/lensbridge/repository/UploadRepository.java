package com.ibrasoft.lensbridge.repository;

import com.ibrasoft.lensbridge.model.Upload;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UploadRepository extends MongoRepository<Upload, UUID> {
    Optional<Upload> findByUuid(UUID uuid);
    List<Upload> findByEventId(UUID eventId);
}
