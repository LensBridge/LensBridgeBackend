package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.GalleryResponseDto;
import com.ibrasoft.lensbridge.repository.EventsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GalleryServiceIntegrationTest {
    @Mock
    private UploadService uploadService;

    @Mock
    private EventsRepository eventsRepository;
    @Mock
    private CloudinaryService cloudinaryService;

    @InjectMocks
    private GalleryService galleryService;

    @Test
    void testGetGalleryItemsByEvent() {
        UUID eventId = UUID.randomUUID();
        when(uploadService.getUploadsByEventId(eventId)).thenReturn(Collections.emptyList());
        GalleryResponseDto response = galleryService.getGalleryItemsByEvent(eventId);
        assertNotNull(response);
        assertTrue(response.getItems().isEmpty());
        verify(uploadService).getUploadsByEventId(eventId);
    }
}
