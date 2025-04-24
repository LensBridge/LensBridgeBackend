package com.ibrasoft.lensbridge.repository;

import com.ibrasoft.lensbridge.model.Upload;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UploadRepository extends MongoRepository<Upload, String> {
    Optional<Upload> findByUuid(String uuid);
}
