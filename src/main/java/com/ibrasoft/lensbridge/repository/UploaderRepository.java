package com.ibrasoft.lensbridge.repository;

import com.ibrasoft.lensbridge.model.Uploader;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UploaderRepository extends MongoRepository<Uploader, UUID> {
    // MongoRepository already provides findById method
}
