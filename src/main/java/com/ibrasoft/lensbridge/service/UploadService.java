package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.model.Upload;
import com.ibrasoft.lensbridge.repository.UploadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadService {

    private final UploadRepository uploadRepository;

    public Upload createUpload(Upload upload) {
        if (upload.getUuid() == null) {
            upload.setUuid(UUID.randomUUID());
        }
        return uploadRepository.save(upload);
    }

    public List<Upload> getAllUploads() {
        return uploadRepository.findAll();
    }

    public Optional<Upload> getUploadById(UUID id) {
        return uploadRepository.findById(id);
    }

    public Optional<Upload> getUploadByUuid(UUID uuid) {
        return uploadRepository.findByUuid(uuid);
    }

    public List<Upload> getUploadsByEventId(UUID eventId) {
        return uploadRepository.findByEventId(eventId);
    }

    public Upload updateUpload(Upload upload) {
        return uploadRepository.save(upload);
    }

    public void deleteUpload(UUID id) {
        uploadRepository.deleteById(id);
    }
}
