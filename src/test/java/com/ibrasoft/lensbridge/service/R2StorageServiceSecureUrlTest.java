package com.ibrasoft.lensbridge.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The approval gate and the shape of the URL handed to clients. R2 is not reachable from CI,
 * so the presigner is mocked and we assert on what we ask it for and what we do with its answer.
 */
@ExtendWith(MockitoExtension.class)
class R2StorageServiceSecureUrlTest {

    private static final String SIGNED_ORIGIN_URL =
            "https://account.r2.cloudflarestorage.com/lensbridge-media/images/abc-123"
                    + "?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=900&X-Amz-Signature=deadbeef";

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner presigner;

    private R2StorageService service;

    @BeforeEach
    void setUp() {
        service = new R2StorageService(s3Client, presigner);
        ReflectionTestUtils.setField(service, "bucketName", "lensbridge-media");
        ReflectionTestUtils.setField(service, "publicUrl", "https://media.lensbridge.tech");
        ReflectionTestUtils.setField(service, "urlExpirationMinutes", 15L);
    }

    private void stubPresigner() {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        try {
            when(presigned.url()).thenReturn(URI.create(SIGNED_ORIGIN_URL).toURL());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);
    }

    @Test
    void secureUrlIsAbsoluteSignedAndExpiring() {
        stubPresigner();

        String url = service.getSecureUrl("images/abc-123", true, false);

        assertThat(url).startsWith("https://media.lensbridge.tech/images/abc-123");
        assertThat(url).contains("X-Amz-Signature=");
        assertThat(url).contains("X-Amz-Expires=");
        // The bucket name must not survive the rewrite to the public domain.
        assertThat(url).doesNotContain("lensbridge-media/");
    }

    @Test
    void secureUrlRequestsTheConfiguredExpiryForTheRightKey() {
        stubPresigner();

        service.getSecureUrl("images/abc-123", true, false);

        ArgumentCaptor<GetObjectPresignRequest> captor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner).presignGetObject(captor.capture());

        GetObjectPresignRequest request = captor.getValue();
        assertThat(request.signatureDuration()).isEqualTo(Duration.ofMinutes(15));
        assertThat(request.getObjectRequest().key()).isEqualTo("images/abc-123");
        assertThat(request.getObjectRequest().bucket()).isEqualTo("lensbridge-media");
    }

    @Test
    void unapprovedContentIsRefusedToUnprivilegedCallersWithoutEverPresigning() {
        assertThatThrownBy(() -> service.getSecureUrl("images/abc-123", false, false))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.getSecureThumbnailUrl("thumbnails/abc-123", false, false))
                .isInstanceOf(SecurityException.class);

        verify(presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    void unapprovedContentIsPresignedForPrivilegedCallers() {
        stubPresigner();

        assertThat(service.getSecureUrl("images/abc-123", false, true)).contains("X-Amz-Signature=");
    }

    @Test
    void downloadUrlExpirationReflectsConfiguration() {
        assertThat(service.getDownloadUrlExpiration()).isEqualTo(Duration.ofMinutes(15));
    }
}
