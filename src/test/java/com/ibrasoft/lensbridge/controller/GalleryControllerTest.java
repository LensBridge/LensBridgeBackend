package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.exception.GlobalExceptionHandler;
import com.ibrasoft.lensbridge.model.upload.MediaEvent;
import com.ibrasoft.lensbridge.repository.upload.EventsRepository;
import com.ibrasoft.lensbridge.repository.upload.UploadRepository;
import com.ibrasoft.lensbridge.service.GalleryService;
import com.ibrasoft.lensbridge.service.R2StorageService;
import com.ibrasoft.lensbridge.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The public gallery end to end through the real paging resolver: what the client may ask for. */
class GalleryControllerTest {

    private final UploadRepository uploadRepository = mock(UploadRepository.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        GalleryService service = new GalleryService(uploadRepository, mock(UserService.class),
                mock(EventsRepository.class), mock(R2StorageService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(new GalleryController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
        when(uploadRepository.findByApprovedTrueAndDeletedAtIsNull(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
    }

    @Test
    void sortingByTheUploadersEmailIsA400NotAnOrderingOracle() throws Exception {
        mockMvc.perform(get("/api/gallery").param("sort", "uploadedBy.email,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("uploadedBy.email")));

        verify(uploadRepository, never()).findByApprovedTrueAndDeletedAtIsNull(any());
    }

    @Test
    void anUnknownSortPropertyIsA400NotA500() throws Exception {
        mockMvc.perform(get("/api/gallery").param("sort", "nope"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createdDateDescendingStillWorks() throws Exception {
        mockMvc.perform(get("/api/gallery").param("sort", "createdDate,desc").param("size", "12"))
                .andExpect(status().isOk());
    }

    @Test
    void anEventThatDoesNotExistIsRefusedBeforeAnyQuery() throws Exception {
        mockMvc.perform(get("/api/gallery/event/{id}", java.util.UUID.randomUUID()))
                .andExpect(status().isBadRequest());
        verify(uploadRepository, never())
                .findByMediaEventAndApprovedTrueAndDeletedAtIsNull(any(MediaEvent.class), any(Pageable.class));
    }
}
