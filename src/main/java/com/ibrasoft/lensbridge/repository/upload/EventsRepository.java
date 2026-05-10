package com.ibrasoft.lensbridge.repository.upload;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ibrasoft.lensbridge.model.upload.Event;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventsRepository extends JpaRepository<Event, UUID> {
    Optional<Event> findEventById(UUID uuid);
}