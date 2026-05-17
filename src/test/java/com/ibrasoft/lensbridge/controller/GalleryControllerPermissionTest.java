package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.security.WebSecurityConfig;
import com.ibrasoft.lensbridge.security.jwt.JwtUtils;
import com.ibrasoft.lensbridge.security.services.UserDetailsServiceImpl;
import com.ibrasoft.lensbridge.service.GalleryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that every endpoint on {@link GalleryController} is genuinely public
 * (matched by the {@code /api/gallery/**} permitAll rule in {@link WebSecurityConfig}).
 * A response that is NOT 401 and NOT 403 when called anonymously is a PASS.
 */
@WebMvcTest(GalleryController.class)
@Import(WebSecurityConfig.class)
@TestPropertySource(properties = {
        "frontend.baseurl=http://localhost:3000",
        "musallahboard.baseurl=http://localhost:4000"
})
class GalleryControllerPermissionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GalleryService galleryService;

    // Required to satisfy WebSecurityConfig wiring.
    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    @MockitoBean
    private JwtUtils jwtUtils;

    @Test
    void getAllUploads_isPublic() throws Exception {
        doReturn(new PageImpl<>(Collections.emptyList()))
                .when(galleryService).getAllApprovedGalleryItems(any());

        mockMvc.perform(get("/api/gallery"))
                .andExpect(status().isOk());
    }

    @Test
    void getGalleryByEvent_isPublic() throws Exception {
        doReturn(new PageImpl<>(Collections.emptyList()))
                .when(galleryService).getGalleryItemsByEvent(any(UUID.class), any());

        mockMvc.perform(get("/api/gallery/event/{eventId}", UUID.randomUUID()))
                .andExpect(status().isOk());
    }
}
