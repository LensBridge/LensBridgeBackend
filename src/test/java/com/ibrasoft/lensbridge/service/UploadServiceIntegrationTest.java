package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.model.upload.Upload;
import com.ibrasoft.lensbridge.repository.UploadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadServiceIntegrationTest {
    @Mock
    private UploadRepository uploadRepository;

    @InjectMocks
    private UploadService uploadService;

    @Test
    void testCreateUpload() {
        Upload upload = new Upload();
        when(uploadRepository.save(any(Upload.class))).thenReturn(upload);
        Upload created = uploadService.createUpload(upload);
        assertNotNull(created);
        verify(uploadRepository).save(upload);
    }
//
//    @Test
//    void testGetAllUploads() {
//        when(uploadRepository.findAll()).thenReturn(Collections.emptyList());
//        assertTrue(uploadService.getAllUploads().isEmpty());
//        verify(uploadRepository).findAll();
//    }

    @Test
    void testGetUploadById() {
        UUID id = UUID.randomUUID();
        Upload upload = new Upload();
        when(uploadRepository.findById(id)).thenReturn(Optional.of(upload));
        Optional<Upload> found = uploadService.getUploadById(id);
        assertTrue(found.isPresent());
        verify(uploadRepository).findById(id);
    }

//    @Test
//    void testGetUploadByUuid() {
//        UUID uuid = UUID.randomUUID();
//        Upload upload = new Upload();
//        when(uploadRepository.findByUuid(uuid)).thenReturn(Optional.of(upload));
//        Optional<Upload> found = uploadService.getUploadByUuid(uuid);
//        assertTrue(found.isPresent());
//        verify(uploadRepository).findByUuid(uuid);
//    }

//    @Test
//    void testGetUploadsByEventId() {
//        UUID eventId = UUID.randomUUID();
//        when(uploadRepository.findByEventId(eventId)).thenReturn(Collections.emptyList());
//        assertTrue(uploadService.getUploadsByEventId(eventId).isEmpty());
//        verify(uploadRepository).findByEventId(eventId);
//    }

    @Test
    void testUpdateUpload() {
        Upload upload = new Upload();
        when(uploadRepository.save(upload)).thenReturn(upload);
        Upload updated = uploadService.updateUpload(upload);
        assertNotNull(updated);
        verify(uploadRepository).save(upload);
    }

    @Test
    void testDeleteUpload() {
        UUID id = UUID.randomUUID();
        doNothing().when(uploadRepository).deleteById(id);
        uploadService.deleteUpload(id);
        verify(uploadRepository).deleteById(id);
    }
}
