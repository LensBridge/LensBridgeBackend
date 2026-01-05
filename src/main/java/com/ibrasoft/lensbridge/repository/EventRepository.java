package com.ibrasoft.lensbridge.repository;

import com.ibrasoft.lensbridge.model.board.Event;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EventRepository extends MongoRepository<Event, UUID> {
}
