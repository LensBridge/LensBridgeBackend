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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private void storedBytes(byte[] bytes) {
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream(bytes))));
    }

    @Test
    void sha256IsComputedOverTheWholeObjectAcrossBufferBoundaries() throws Exception {
        byte[] bytes = new byte[3 * 64 * 1024 + 123]; // not a multiple of the read buffer
        new java.util.Random(7).nextBytes(bytes);
        storedBytes(bytes);

        String expected = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(bytes));

        assertThat(service.calculateSha256Hash("images/a")).isEqualTo(expected);
    }

    @Test
    void sha256OfAnEmptyObjectIsTheEmptyDigest() throws Exception {
        storedBytes(new byte[0]);

        assertThat(service.calculateSha256Hash("images/a"))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void exposesTheConfiguredUrlExpiry() {
        ReflectionTestUtils.setField(service, "urlExpirationMinutes", 42L);

        assertThat(service.getUrlExpirationMinutes()).isEqualTo(42L);
    }

    @Test
    void anUnapprovedFileIsOnlySignedForAdmins() {
        assertThatThrownBy(() -> service.getSecureUrlForStoredFile("images/a", false, false))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.getSecureThumbnailUrlOrOriginal("images/a", null, false, false))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void theThumbnailUrlFallsBackToTheOriginalUntilTheThumbnailExists() {
        R2StorageService spy = org.mockito.Mockito.spy(service);
        org.mockito.Mockito.doReturn("signed:images/a").when(spy).generatePresignedDownloadUrl("images/a");
        org.mockito.Mockito.doReturn("signed:thumbnails/a").when(spy).generatePresignedDownloadUrl("thumbnails/a");

        assertThat(spy.getSecureThumbnailUrlOrOriginal("images/a", "thumbnails/a", true, false))
                .isEqualTo("signed:thumbnails/a");
        assertThat(spy.getSecureThumbnailUrlOrOriginal("images/a", null, true, false))
                .isEqualTo("signed:images/a");
        assertThat(spy.getSecureThumbnailUrlOrOriginal("images/a", " ", true, false))
                .isEqualTo("signed:images/a");
    }

    @Test
    void aStoredFileUrlMayBeAKeyOrAFullUrl() {
        R2StorageService spy = org.mockito.Mockito.spy(service);
        org.mockito.Mockito.doReturn("signed").when(spy).generatePresignedDownloadUrl("images/a");

        assertThat(spy.getSecureUrlForStoredFile("images/a", true, false)).isEqualTo("signed");
        assertThat(spy.getSecureUrlForStoredFile("https://x.example/bucket/images/a", true, false)).isEqualTo("signed");
    }
}
