package com.ibrasoft.lensbridge.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The two helpers the offline bundle uses to pull poster images out of R2. */
class R2StorageServiceTest {

    private final S3Client s3Client = mock(S3Client.class);
    private R2StorageService service;

    @BeforeEach
    void setUp() {
        service = new R2StorageService(s3Client, mock(S3Presigner.class));
        ReflectionTestUtils.setField(service, "bucketName", "bucket");
        ReflectionTestUtils.setField(service, "publicUrl", "https://cdn.example.com");
    }

    @Test
    void objectKeyFromPublicUrlStripsThePublicPrefix() {
        assertThat(service.objectKeyFromPublicUrl("https://cdn.example.com/posters/a b.jpg"))
                .isEqualTo("posters/a b.jpg");
    }

    @Test
    void objectKeyFromPublicUrlToleratesATrailingSlashOnThePublicUrl() {
        ReflectionTestUtils.setField(service, "publicUrl", "https://cdn.example.com/");

        assertThat(service.objectKeyFromPublicUrl("https://cdn.example.com//posters/a.jpg"))
                .isEqualTo("posters/a.jpg");
    }

    @Test
    void objectKeyFromPublicUrlFallsBackToPathExtraction() {
        assertThat(service.objectKeyFromPublicUrl("https://other.example.com/bucket/posters/a.jpg"))
                .isEqualTo("posters/a.jpg");
    }

    @Test
    void getObjectReturnsBytesAndContentType() throws Exception {
        byte[] bytes = "image".getBytes(StandardCharsets.UTF_8);
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().contentType("image/png").build(),
                AbortableInputStream.create(new ByteArrayInputStream(bytes))));

        R2StorageService.R2Object object = service.getObject("posters/a.png");

        assertThat(object.objectKey()).isEqualTo("posters/a.png");
        assertThat(object.bytes()).isEqualTo(bytes);
        assertThat(object.contentType()).isEqualTo("image/png");
    }
}
