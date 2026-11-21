package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.upload.response.PresignedUploadResponse;
import com.ibrasoft.lensbridge.dto.upload.response.UploadCompletionResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.exception.DailyLimitExceededException;
import com.ibrasoft.lensbridge.exception.EventNotAcceptingUploadsException;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.upload.Upload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The complete step takes an object key from the client, so most of this pins what must happen
 * before R2 is touched: the key has to be one this user was issued, and only then may a failed
 * check delete the object.
 */
class UploadWorkflowServiceTest {

    private static final String SHA = "a".repeat(64);
    private static final long SIZE = 2048;

    private final UploadService uploadService = mock(UploadService.class);
    private final R2StorageService r2 = mock(R2StorageService.class);
    private final UploadLimitsService limits = mock(UploadLimitsService.class);
    private final EventsService events = mock(EventsService.class);
    private final ImageProcessingService imageProcessing = mock(ImageProcessingService.class);

    private final UUID eventId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private UploadWorkflowService service;

    @BeforeEach
    void setUp() {
        when(r2.getUrlExpirationMinutes()).thenReturn(15L);
        when(r2.generatePresignedUploadUrl(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn("https://r2.example/put");
        when(events.isEventAcceptingUploads(eventId)).thenReturn(true);
        service = new UploadWorkflowService(uploadService, r2, limits, events, imageProcessing);
    }

    /** Presigns, then forgets the calls that made, so a test only sees what completion does. */
    private String issueKey(UUID forUser) {
        String key = service.initiateUpload(eventId, "a.jpg", "image/jpeg", SIZE, SHA, forUser, Role.USER)
                .getObjectKey();
        org.mockito.Mockito.clearInvocations(r2, limits, events, uploadService);
        return key;
    }

    private void storedObject(String key, long size, String contentType, String hash) throws Exception {
        when(r2.objectExists(key)).thenReturn(true);
        when(r2.getObjectMetadata(key)).thenReturn(new R2StorageService.R2ObjectMetadata(
                key, size, contentType, "etag", Instant.now(), java.util.Map.of()));
        when(r2.calculateSha256Hash(key)).thenReturn(hash);
    }

    private UploadCompletionResponse complete(String key) {
        return service.completeUpload(eventId, key, "a.jpg", "image/jpeg", SIZE,
                null, null, false, SHA, userId, Role.USER);
    }

    /** Storage is only ever read or deleted after the key checks pass. */
    private void verifyStorageUntouched() throws Exception {
        verify(r2, never()).objectExists(anyString());
        verify(r2, never()).getObjectMetadata(anyString());
        verify(r2, never()).calculateSha256Hash(anyString());
        verify(r2, never()).deleteObject(anyString());
    }

    private ApiResponseException expectRejected(String key) {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> complete(key));
        assertThat(thrown).isInstanceOf(ApiResponseException.class);
        assertThat(((ApiResponseException) thrown).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        return (ApiResponseException) thrown;
    }

    // ── presign ───────────────────────────────────────────────────────────────

    @Test
    void presignReportsTheConfiguredExpiryNotAHardCodedOne() {
        when(r2.getUrlExpirationMinutes()).thenReturn(30L);

        PresignedUploadResponse response = service.initiateUpload(
                eventId, "a.jpg", "image/jpeg", SIZE, SHA, userId, Role.USER);

        assertThat(response.getExpiresInMinutes()).isEqualTo(30);
        assertThat(response.getObjectKey()).matches("images/[0-9a-f-]{36}");
    }

    @Test
    void presignRefusesAClosedEventWithoutIssuingAKey() {
        when(events.isEventAcceptingUploads(eventId)).thenReturn(false);

        assertThatThrownBy(() -> issueKey(userId)).isInstanceOf(EventNotAcceptingUploadsException.class);
        verify(r2, never()).generatePresignedUploadUrl(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    // ── object key validation ─────────────────────────────────────────────────

    @Test
    void aKeyOutsideTheIssuedFormatIsRefusedWithoutTouchingStorage() throws Exception {
        for (String key : new String[] {
                "posters/some-poster.jpg",
                "thumbnails/" + UUID.randomUUID(),
                "images/../posters/x",
                "images/not-a-uuid",
                "images/" + UUID.randomUUID() + "/extra",
                "" }) {
            expectRejected(key);
        }
        verifyStorageUntouched();
    }

    @Test
    void aWellFormedKeyNeverIssuedToAnyoneIsRefusedWithoutTouchingStorage() throws Exception {
        expectRejected("images/" + UUID.randomUUID());

        verifyStorageUntouched();
        verify(uploadService, never()).createUpload(any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void aKeyIssuedToSomeoneElseCannotBeClaimedOrDeleted() throws Exception {
        String victimsKey = issueKey(UUID.randomUUID());
        storedObject(victimsKey, SIZE, "image/jpeg", "b".repeat(64)); // wrong hash on purpose

        expectRejected(victimsKey);

        verify(r2, never()).deleteObject(anyString());
        verify(r2, never()).objectExists(anyString());
        verify(uploadService, never()).createUpload(any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void aKeyAlreadyBelongingToAnUploadIsRefusedAndItsObjectKept() throws Exception {
        String key = issueKey(userId);
        storedObject(key, SIZE, "image/jpeg", SHA);
        when(uploadService.isObjectKeyInUse(key)).thenReturn(true);

        expectRejected(key);

        verify(r2, never()).deleteObject(anyString());
        verify(uploadService, never()).createUpload(any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void completesAnIssuedKeyAndThenForgetsIt() throws Exception {
        String key = issueKey(userId);
        storedObject(key, SIZE, "image/jpeg", SHA.toUpperCase());
        Upload upload = new Upload();
        upload.setUuid(UUID.randomUUID());
        when(uploadService.createUpload(eq(key), any(), eq(eventId), any(), any(), anyBoolean(), eq(userId)))
                .thenReturn(upload);

        UploadCompletionResponse response = complete(key);

        assertThat(response.getUploadId()).isEqualTo(upload.getUuid());
        assertThat(response.isVerified()).isTrue();
        verify(imageProcessing).generateThumbnail(upload);
        verify(limits).validateUpload(userId, Role.USER, SIZE, "image/jpeg");

        // a second completion of the same key is a replay and must not reach storage again
        org.mockito.Mockito.clearInvocations(r2);
        expectRejected(key);
        verifyStorageUntouched();
    }

    // ── limits at completion ──────────────────────────────────────────────────

    @Test
    void limitsAreCheckedAgainstWhatStorageReportsNotWhatTheClientClaimed() throws Exception {
        String key = issueKey(userId);
        // the client claims 2048 bytes of image/jpeg; the object really is that size but R2 says it is a GIF
        storedObject(key, SIZE, "image/gif", SHA);
        doThrow(new com.ibrasoft.lensbridge.exception.InvalidContentTypeException("image/gif"))
                .when(limits).validateUpload(userId, Role.ADMIN, SIZE, "image/gif");

        assertThatThrownBy(() -> service.completeUpload(eventId, key, "a.jpg", "image/jpeg", SIZE,
                null, null, false, SHA, userId, Role.ADMIN))
                .isInstanceOf(com.ibrasoft.lensbridge.exception.InvalidContentTypeException.class);

        verify(r2).deleteObject(key);
        verify(uploadService, never()).createUpload(any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void aLimitReachedBetweenPresignAndCompleteRejectsAndDeletesTheObject() throws Exception {
        String key = issueKey(userId);
        storedObject(key, SIZE, "image/jpeg", SHA);
        doThrow(new DailyLimitExceededException(10, 10, "user"))
                .when(limits).validateUpload(userId, Role.USER, SIZE, "image/jpeg");

        assertThatThrownBy(() -> complete(key)).isInstanceOf(DailyLimitExceededException.class);

        verify(r2).deleteObject(key);
    }

    @Test
    void anEventThatClosedAfterPresignRejectsAndDeletesTheObject() throws Exception {
        String key = issueKey(userId);
        storedObject(key, SIZE, "image/jpeg", SHA);
        when(events.isEventAcceptingUploads(eventId)).thenReturn(false);

        assertThatThrownBy(() -> complete(key)).isInstanceOf(EventNotAcceptingUploadsException.class);

        verify(r2).deleteObject(key);
        verify(uploadService, never()).createUpload(any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    // ── integrity ─────────────────────────────────────────────────────────────

    @Test
    void aHashMismatchOnAnIssuedKeyDeletesTheObject() throws Exception {
        String key = issueKey(userId);
        storedObject(key, SIZE, "image/jpeg", "b".repeat(64));

        expectRejected(key);

        verify(r2).deleteObject(key);
    }

    @Test
    void aSizeMismatchOnAnIssuedKeyDeletesTheObject() throws Exception {
        String key = issueKey(userId);
        storedObject(key, SIZE + 1, "image/jpeg", SHA);

        expectRejected(key);

        verify(r2).deleteObject(key);
        verify(limits, never()).validateUpload(any(), any(), org.mockito.ArgumentMatchers.anyLong(), anyString());
    }

    @Test
    void anObjectThatCannotBeReadIsNotDeletedSoTheClientCanRetry() throws Exception {
        String key = issueKey(userId);
        storedObject(key, SIZE, "image/jpeg", SHA);
        when(r2.calculateSha256Hash(key)).thenThrow(new java.io.IOException("connection reset"));

        expectRejected(key);

        verify(r2, never()).deleteObject(anyString());
    }

    @Test
    void anObjectNotYetInStorageIsRefusedButTheKeyStaysUsable() throws Exception {
        String key = issueKey(userId);
        when(r2.objectExists(key)).thenReturn(false);

        expectRejected(key);

        // the client's PUT finishes, and the same key now completes
        storedObject(key, SIZE, "image/jpeg", SHA);
        Upload upload = new Upload();
        upload.setUuid(UUID.randomUUID());
        when(uploadService.createUpload(eq(key), any(), eq(eventId), any(), any(), anyBoolean(), eq(userId)))
                .thenReturn(upload);
        assertThat(complete(key).getUploadId()).isEqualTo(upload.getUuid());
    }
}
