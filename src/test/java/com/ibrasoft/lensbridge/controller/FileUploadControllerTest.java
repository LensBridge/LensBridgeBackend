package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.config.TestSecurityConfig;
import com.ibrasoft.lensbridge.model.upload.Upload;
import com.ibrasoft.lensbridge.service.CloudinaryService;
import com.ibrasoft.lensbridge.service.UploadService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestSecurityConfig.class)
@WebMvcTest(FileUploadController.class)
class FileUploadControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UploadService uploadService;
    @MockBean
    private CloudinaryService cloudinaryService;

    @Test
    void testUploadFilesImage() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "test.jpg", "image/jpeg", "test image".getBytes());
        Mockito.when(cloudinaryService.uploadImage(any(byte[].class), any(String.class))).thenReturn("http://test.com/image.jpg");
        Mockito.when(uploadService.createUpload(any(Upload.class))).thenReturn(new Upload());
        mockMvc.perform(multipart("/api/upload/" + UUID.randomUUID() + "/batch")
                .file(file)
                .param("instagramHandle", "testuser")
                .param("description", "desc"))
                .andExpect(status().isOk());
    }

    @Test
    void testUploadFilesVideo() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "test.mp4", "video/mp4", "test video".getBytes());
        Mockito.when(cloudinaryService.uploadVideo(any(byte[].class), any(String.class))).thenReturn("http://test.com/video.mp4");
        Mockito.when(uploadService.createUpload(any(Upload.class))).thenReturn(new Upload());
        mockMvc.perform(multipart("/api/upload/" + UUID.randomUUID() + "/batch")
                .file(file)
                .param("instagramHandle", "testuser")
                .param("description", "desc"))
                .andExpect(status().isOk());
    }
}
