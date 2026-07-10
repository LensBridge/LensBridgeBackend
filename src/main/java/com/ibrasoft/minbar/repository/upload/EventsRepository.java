package com.ibrasoft.minbar.repository.upload;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ibrasoft.minbar.model.upload.MediaEvent;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventsRepository extends JpaRepository<MediaEvent, UUID> {
    Optional<MediaEvent> findEventById(UUID uuid);
}