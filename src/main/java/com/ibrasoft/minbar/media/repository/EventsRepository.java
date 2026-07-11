package com.ibrasoft.minbar.media.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ibrasoft.minbar.media.model.MediaEvent;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventsRepository extends JpaRepository<MediaEvent, UUID> {
    Optional<MediaEvent> findEventById(UUID uuid);
}