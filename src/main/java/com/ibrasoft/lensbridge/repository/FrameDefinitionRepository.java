package com.ibrasoft.lensbridge.repository;

import com.ibrasoft.lensbridge.model.board.FrameDefinition;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FrameDefinitionRepository extends MongoRepository<FrameDefinition, String> {
}
