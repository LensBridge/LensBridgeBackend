package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.repository.EventsRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

//    @Test
//    void testGetGalleryItemsByEvent() {
//        UUID eventId = UUID.randomUUID();
//        when(uploadService.getUploadsByEventId(eventId)).thenReturn(Collections.emptyList());
//        GalleryResponseDto response = galleryService.getGalleryItemsByEvent(eventId);
//        assertNotNull(response);
//        assertTrue(response.getItems().isEmpty());
//        verify(uploadService).getUploadsByEventId(eventId);
//    }
}
