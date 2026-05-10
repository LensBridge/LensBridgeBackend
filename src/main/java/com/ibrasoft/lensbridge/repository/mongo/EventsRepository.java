package com.ibrasoft.lensbridge.repository;

import com.ibrasoft.lensbridge.model.event.Event;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventsRepository extends MongoRepository<Event, UUID> {
    Optional<Event> findEventById(UUID uuid);
}