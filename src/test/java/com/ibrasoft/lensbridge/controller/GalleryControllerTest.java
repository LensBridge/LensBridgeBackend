package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.config.TestSecurityConfig;
import com.ibrasoft.lensbridge.dto.GalleryResponseDto;
import com.ibrasoft.lensbridge.service.GalleryService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestSecurityConfig.class)
@WebMvcTest(GalleryController.class)
class GalleryControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GalleryService galleryService;

//    @Test
//    void testGetAllUploads() throws Exception {
//        Mockito.when(galleryService.getAllGalleryItems()).thenReturn(new GalleryResponseDto());
//        mockMvc.perform(get("/api/gallery"))
//                .andExpect(status().isOk())
//                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
//    }

    @Test
    void testGetGalleryByEvent() throws Exception {
        Mockito.when(galleryService.getGalleryItemsByEvent(any(UUID.class))).thenReturn(new GalleryResponseDto());
        mockMvc.perform(get("/api/gallery/event/" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
}
