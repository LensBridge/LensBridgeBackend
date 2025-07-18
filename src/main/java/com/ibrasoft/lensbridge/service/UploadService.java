package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.response.AdminUploadDto;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.event.Event;
import com.ibrasoft.lensbridge.model.upload.Upload;
import com.ibrasoft.lensbridge.repository.UploadRepository;
import com.ibrasoft.lensbridge.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadService {

    private final UploadRepository uploadRepository;
    private final UserRepository userRepository;
    private final EventsService eventsService;

    public Upload createUpload(Upload upload) {
        if (upload.getUuid() == null) {
            upload.setUuid(UUID.randomUUID());
        }
        return uploadRepository.save(upload);
    }

    public Page<Upload> getAllUploads(Pageable pageable) {
        return uploadRepository.findAll(pageable);
    }

    public Optional<Upload> getUploadById(UUID id) {
        return uploadRepository.findById(id);
    }

    public Upload updateUpload(Upload upload) {
        return uploadRepository.save(upload);
    }

    public void deleteUpload(UUID id) {
        uploadRepository.deleteById(id);
    }

    /**
     * Get all uploads with user information populated for admin interface.
     * This method fetches uploads and includes the uploader's name and details.
     */
    public Page<AdminUploadDto> getAllUploadsForAdmin(Pageable pageable) {
        Page<Upload> uploads = uploadRepository.findAll(pageable);
        return uploads.map(this::convertToAdminUploadDto);
    }

    /**
     * Get uploads by approval status with user information for admin interface.
     */
    public Page<AdminUploadDto> getUploadsByApprovalStatus(boolean approved, Pageable pageable) {
        Page<Upload> uploads = uploadRepository.findByApproved(approved, pageable);
        return uploads.map(this::convertToAdminUploadDto);
    }

    /**
     * Get uploads by featured status with user information for admin interface.
     */
    public Page<AdminUploadDto> getUploadsByFeaturedStatus(boolean featured, Pageable pageable) {
        Page<Upload> uploads = uploadRepository.findByFeatured(featured, pageable);
        return uploads.map(this::convertToAdminUploadDto);
    }

    /**
     * Convert Upload entity to AdminUploadDto with user information populated.
     */
    private AdminUploadDto convertToAdminUploadDto(Upload upload) {
        AdminUploadDto dto = new AdminUploadDto();
        
        // Copy upload fields
        dto.setUuid(upload.getUuid());
        dto.setFileName(upload.getFileName());
        dto.setFileUrl(upload.getFileUrl());
        dto.setUploadDescription(upload.getUploadDescription());
        dto.setInstagramHandle(upload.getInstagramHandle());
        dto.setUploadedBy(upload.getUploadedBy());
        dto.setEventId(upload.getEventId());
        dto.setCreatedDate(upload.getCreatedDate());
        dto.setApproved(upload.isApproved());
        dto.setFeatured(upload.isFeatured());
        dto.setAnon(upload.isAnon());
        dto.setContentType(upload.getContentType());
    
        Optional<User> userOpt = userRepository.findById(upload.getUploadedBy());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            dto.setUploaderFirstName(user.getFirstName());
            dto.setUploaderLastName(user.getLastName());
            dto.setUploaderEmail(user.getEmail());
            dto.setUploaderStudentNumber(user.getStudentNumber());
        }
        
        // Fetch and populate event information
        if (upload.getEventId() != null) {
            Optional<Event> eventOpt = eventsService.getEventById(upload.getEventId());
            if (eventOpt.isPresent()) {
                dto.setEventName(eventOpt.get().getName());
            }
        }
        
        return dto;
    }
}
