package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.response.AdminUploadDto;
import com.ibrasoft.lensbridge.dto.response.GalleryItemDto;
import com.ibrasoft.lensbridge.dto.response.UserStatsResponse;
import com.ibrasoft.lensbridge.exception.FileProcessingException;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.event.Event;
import com.ibrasoft.lensbridge.model.upload.Upload;
import com.ibrasoft.lensbridge.model.upload.UploadType;
import com.ibrasoft.lensbridge.repository.UploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UploadService}.
 *
 * <p>Note: the task brief described a target SQL-migration state with soft-delete
 * (deletedAt/deletedBy), {@code findByDeletedAtIsNull} queries, and separate
 * {@code UploadLimitsService}/{@code UploadWorkflowService} classes. None of those
 * exist on this branch — the codebase is still MongoDB-based and concentrates
 * approve/feature/delete and per-user daily-limit logic in {@link UploadService}.
 * These tests therefore document the real behavior present in source.
 */
@ExtendWith(MockitoExtension.class)
class UploadServiceTest {

    @Mock
    private UploadRepository uploadRepository;

    @Mock
    private UserService userService;

    @Mock
    private EventsService eventsService;

    @Mock
    private R2StorageService r2StorageService;

    @InjectMocks
    private UploadService uploadService;

    @Captor
    private ArgumentCaptor<Upload> uploadCaptor;

    private UUID uploadId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        uploadId = UUID.randomUUID();
        userId = UUID.randomUUID();
        ReflectionTestUtils.setField(uploadService, "defaultApproved", false);
        ReflectionTestUtils.setField(uploadService, "defaultFeatured", false);
    }

    private Upload newUpload() {
        Upload upload = new Upload();
        upload.setUuid(uploadId);
        upload.setUploadedBy(userId);
        upload.setFileUrl("https://cdn.example.com/uploads/file.jpg");
        upload.setThumbnailUrl("uploads/thumb.jpg");
        upload.setContentType(UploadType.IMAGE);
        upload.setCreatedDate(LocalDateTime.of(2026, 5, 15, 10, 0));
        return upload;
    }

    // ---------- approveUpload ----------

    @Test
    void approveUpload_setsApprovedAndSaves_whenUploadExists() {
        Upload upload = newUpload();
        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));

        uploadService.approveUpload(uploadId);

        verify(uploadRepository).save(uploadCaptor.capture());
        assertThat(uploadCaptor.getValue().isApproved()).isTrue();
    }

    @Test
    void approveUpload_throwsAndDoesNotSave_whenUploadMissing() {
        when(uploadRepository.findById(uploadId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> uploadService.approveUpload(uploadId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Upload not found");

        verify(uploadRepository, never()).save(any());
    }

    // ---------- unapproveUpload ----------

    @Test
    void unapproveUpload_clearsApprovedAndSaves_whenUploadExists() {
        Upload upload = newUpload();
        upload.setApproved(true);
        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));

        uploadService.unapproveUpload(uploadId);

        verify(uploadRepository).save(uploadCaptor.capture());
        assertThat(uploadCaptor.getValue().isApproved()).isFalse();
    }

    @Test
    void unapproveUpload_throws_whenUploadMissing() {
        when(uploadRepository.findById(uploadId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> uploadService.unapproveUpload(uploadId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Upload not found");

        verify(uploadRepository, never()).save(any());
    }

    // ---------- featureUpload / unfeatureUpload ----------

    @Test
    void featureUpload_setsFeaturedAndSaves_whenUploadExists() {
        Upload upload = newUpload();
        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));

        uploadService.featureUpload(uploadId);

        verify(uploadRepository).save(uploadCaptor.capture());
        assertThat(uploadCaptor.getValue().isFeatured()).isTrue();
    }

    @Test
    void featureUpload_throws_whenUploadMissing() {
        when(uploadRepository.findById(uploadId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> uploadService.featureUpload(uploadId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Upload not found");

        verify(uploadRepository, never()).save(any());
    }

    @Test
    void unfeatureUpload_clearsFeaturedAndSaves_whenUploadExists() {
        Upload upload = newUpload();
        upload.setFeatured(true);
        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));

        uploadService.unfeatureUpload(uploadId);

        verify(uploadRepository).save(uploadCaptor.capture());
        assertThat(uploadCaptor.getValue().isFeatured()).isFalse();
    }

    @Test
    void unfeatureUpload_throws_whenUploadMissing() {
        when(uploadRepository.findById(uploadId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> uploadService.unfeatureUpload(uploadId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Upload not found");

        verify(uploadRepository, never()).save(any());
    }

    // ---------- countUploadsToday / hasReachedDailyLimit ----------

    @Test
    void countUploadsToday_queriesRepositoryWithTodayBoundaries() {
        when(uploadRepository.countByUploadedByAndCreatedDateBetween(
                eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(3L);

        long count = uploadService.countUploadsToday(userId);

        assertThat(count).isEqualTo(3L);
        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(uploadRepository).countByUploadedByAndCreatedDateBetween(
                eq(userId), startCaptor.capture(), endCaptor.capture());

        LocalDate today = LocalDate.now();
        assertThat(startCaptor.getValue()).isEqualTo(today.atStartOfDay());
        assertThat(endCaptor.getValue()).isEqualTo(today.atTime(23, 59, 59));
    }

    @Test
    void hasReachedDailyLimit_true_whenCountEqualsLimit() {
        when(uploadRepository.countByUploadedByAndCreatedDateBetween(
                eq(userId), any(), any())).thenReturn(10L);

        assertThat(uploadService.hasReachedDailyLimit(userId, 10)).isTrue();
    }

    @Test
    void hasReachedDailyLimit_true_whenCountExceedsLimit() {
        when(uploadRepository.countByUploadedByAndCreatedDateBetween(
                eq(userId), any(), any())).thenReturn(11L);

        assertThat(uploadService.hasReachedDailyLimit(userId, 10)).isTrue();
    }

    @Test
    void hasReachedDailyLimit_false_whenCountBelowLimit() {
        when(uploadRepository.countByUploadedByAndCreatedDateBetween(
                eq(userId), any(), any())).thenReturn(9L);

        assertThat(uploadService.hasReachedDailyLimit(userId, 10)).isFalse();
    }

    @Test
    void hasReachedDailyLimit_respectsRoleSpecificLimits() {
        // user 10 / admin 500 / root 1000 thresholds are supplied by the caller;
        // verify the boundary comparison works for each role's limit.
        when(uploadRepository.countByUploadedByAndCreatedDateBetween(eq(userId), any(), any()))
                .thenReturn(500L);
        assertThat(uploadService.hasReachedDailyLimit(userId, 10)).isTrue();   // user limit
        assertThat(uploadService.hasReachedDailyLimit(userId, 500)).isTrue();  // admin limit
        assertThat(uploadService.hasReachedDailyLimit(userId, 1000)).isFalse(); // root limit
    }

    // ---------- createDirectUpload ----------

    @Test
    void createDirectUpload_imageContentType_buildsImageUploadAndSaves() {
        UUID eventId = UUID.randomUUID();

        Upload result = uploadService.createDirectUpload(
                "uploads/key.jpg", "key.jpg", "image/jpeg",
                eventId, "desc", "@handle", false, userId);

        verify(uploadRepository).save(uploadCaptor.capture());
        Upload saved = uploadCaptor.getValue();
        assertThat(saved.getContentType()).isEqualTo(UploadType.IMAGE);
        assertThat(saved.getFileName()).isEqualTo("key.jpg");
        assertThat(saved.getFileUrl()).isEqualTo("uploads/key.jpg");
        assertThat(saved.getUploadedBy()).isEqualTo(userId);
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.isApproved()).isFalse();
        assertThat(saved.isFeatured()).isFalse();
        assertThat(result).isSameAs(saved);
    }

    @Test
    void createDirectUpload_videoContentType_buildsVideoUpload() {
        uploadService.createDirectUpload(
                "uploads/clip.mp4", "clip.mp4", "video/mp4",
                null, "desc", "@h", true, userId);

        verify(uploadRepository).save(uploadCaptor.capture());
        assertThat(uploadCaptor.getValue().getContentType()).isEqualTo(UploadType.VIDEO);
        assertThat(uploadCaptor.getValue().isAnon()).isTrue();
    }

    @Test
    void createDirectUpload_unknownContentType_defaultsToImage() {
        uploadService.createDirectUpload(
                "uploads/x.bin", "x.bin", "application/octet-stream",
                null, "desc", "@h", false, userId);

        verify(uploadRepository).save(uploadCaptor.capture());
        assertThat(uploadCaptor.getValue().getContentType()).isEqualTo(UploadType.IMAGE);
    }

    @Test
    void createDirectUpload_nullContentType_defaultsToImage() {
        uploadService.createDirectUpload(
                "uploads/x", "x", null,
                null, "desc", "@h", false, userId);

        verify(uploadRepository).save(uploadCaptor.capture());
        assertThat(uploadCaptor.getValue().getContentType()).isEqualTo(UploadType.IMAGE);
    }

    @Test
    void createDirectUpload_honorsDefaultApprovedAndFeaturedConfig() {
        ReflectionTestUtils.setField(uploadService, "defaultApproved", true);
        ReflectionTestUtils.setField(uploadService, "defaultFeatured", true);

        uploadService.createDirectUpload(
                "uploads/k.jpg", "k.jpg", "image/jpeg",
                null, "d", "@h", false, userId);

        verify(uploadRepository).save(uploadCaptor.capture());
        assertThat(uploadCaptor.getValue().isApproved()).isTrue();
        assertThat(uploadCaptor.getValue().isFeatured()).isTrue();
    }

    @Test
    void createDirectUpload_wrapsRepositoryFailureInFileProcessingException() {
        when(uploadRepository.save(any())).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> uploadService.createDirectUpload(
                "uploads/k.jpg", "k.jpg", "image/jpeg",
                null, "d", "@h", false, userId))
                .isInstanceOf(FileProcessingException.class)
                .hasMessageContaining("Failed to create upload record");
    }

    // ---------- simple pass-through queries ----------

    @Test
    void getAllUploads_delegatesToRepository() {
        Pageable pageable = PageRequest.of(0, 5);
        Page<Upload> page = new PageImpl<>(List.of(newUpload()));
        when(uploadRepository.findAll(pageable)).thenReturn(page);

        assertThat(uploadService.getAllUploads(pageable)).isSameAs(page);
    }

    @Test
    void getUploadsByEvent_delegatesToRepository() {
        UUID eventId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 5);
        Page<Upload> page = new PageImpl<>(List.of(newUpload()));
        when(uploadRepository.findByEventId(eventId, pageable)).thenReturn(page);

        assertThat(uploadService.getUploadsByEvent(eventId, pageable)).isSameAs(page);
    }

    @Test
    void getUploadById_delegatesToRepository() {
        Upload upload = newUpload();
        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));

        assertThat(uploadService.getUploadById(uploadId)).contains(upload);
    }

    @Test
    void updateUpload_savesAndReturnsEntity() {
        Upload upload = newUpload();
        when(uploadRepository.save(upload)).thenReturn(upload);

        assertThat(uploadService.updateUpload(upload)).isSameAs(upload);
    }

    @Test
    void getUploadsByUploadedBy_delegatesToRepository() {
        Pageable pageable = PageRequest.of(0, 5);
        Page<Upload> page = new PageImpl<>(List.of(newUpload()));
        when(uploadRepository.findByUploadedBy(userId, pageable)).thenReturn(page);

        assertThat(uploadService.getUploadsByUploadedBy(userId, pageable)).isSameAs(page);
    }

    // ---------- deleteUpload ----------

    @Test
    void deleteUpload_deletesR2ObjectsAndDbRecord_whenUploadExists() {
        Upload upload = newUpload();
        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));
        when(r2StorageService.extractObjectKeyFromUrl(upload.getFileUrl()))
                .thenReturn("uploads/file.jpg");

        uploadService.deleteUpload(uploadId);

        verify(r2StorageService).deleteObject("uploads/file.jpg");
        verify(r2StorageService).deleteObject("uploads/thumb.jpg");
        verify(uploadRepository).deleteById(uploadId);
    }

    @Test
    void deleteUpload_stillDeletesDbRecord_whenUploadMissing() {
        when(uploadRepository.findById(uploadId)).thenReturn(Optional.empty());

        uploadService.deleteUpload(uploadId);

        verifyNoInteractions(r2StorageService);
        verify(uploadRepository).deleteById(uploadId);
    }

    @Test
    void deleteUpload_continuesToDbDeletion_whenR2DeletionFails() {
        Upload upload = newUpload();
        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));
        when(r2StorageService.extractObjectKeyFromUrl(any()))
                .thenThrow(new RuntimeException("r2 unavailable"));

        uploadService.deleteUpload(uploadId);

        verify(uploadRepository).deleteById(uploadId);
    }

    @Test
    void deleteUpload_skipsThumbnailDeletion_whenThumbnailBlank() {
        Upload upload = newUpload();
        upload.setThumbnailUrl("  ");
        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));
        when(r2StorageService.extractObjectKeyFromUrl(upload.getFileUrl()))
                .thenReturn("uploads/file.jpg");

        uploadService.deleteUpload(uploadId);

        verify(r2StorageService).deleteObject("uploads/file.jpg");
        verify(r2StorageService, never()).deleteObject("  ");
        verify(uploadRepository).deleteById(uploadId);
    }

    // ---------- deleteUserUpload (ownership) ----------

    @Test
    void deleteUserUpload_deletes_whenUserOwnsUpload() {
        Upload upload = newUpload();
        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));
        when(r2StorageService.extractObjectKeyFromUrl(upload.getFileUrl()))
                .thenReturn("uploads/file.jpg");

        uploadService.deleteUserUpload(uploadId, userId);

        verify(uploadRepository).deleteById(uploadId);
    }

    @Test
    void deleteUserUpload_throwsSecurityException_whenUserDoesNotOwnUpload() {
        Upload upload = newUpload();
        upload.setUploadedBy(UUID.randomUUID());
        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));

        assertThatThrownBy(() -> uploadService.deleteUserUpload(uploadId, userId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("only delete your own uploads");

        verify(uploadRepository, never()).deleteById(any());
    }

    @Test
    void deleteUserUpload_throwsIllegalArgument_whenUploadMissing() {
        when(uploadRepository.findById(uploadId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> uploadService.deleteUserUpload(uploadId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Upload not found");

        verify(uploadRepository, never()).deleteById(any());
    }

    // ---------- getUserStats ----------

    @Test
    void getUserStats_computesPendingAsTotalMinusApproved() {
        when(uploadRepository.countByUploadedBy(userId)).thenReturn(10L);
        when(uploadRepository.countByUploadedByAndApproved(userId, true)).thenReturn(7L);
        when(uploadRepository.countByUploadedByAndFeatured(userId, true)).thenReturn(2L);

        UserStatsResponse stats = uploadService.getUserStats(userId);

        assertThat(stats.getTotalUploads()).isEqualTo(10);
        assertThat(stats.getApprovedUploads()).isEqualTo(7);
        assertThat(stats.getFeaturedUploads()).isEqualTo(2);
        assertThat(stats.getPendingUploads()).isEqualTo(3);
    }

    // ---------- getUploadsByApprovalStatus / FeaturedStatus (admin DTO mapping) ----------

    @Test
    void getUploadsByApprovalStatus_mapsToAdminDtoUsingApprovedQuery() {
        Pageable pageable = PageRequest.of(0, 5);
        Upload upload = newUpload();
        when(uploadRepository.findByApproved(true, pageable))
                .thenReturn(new PageImpl<>(List.of(upload)));
        when(r2StorageService.extractObjectKeyFromUrl(any())).thenReturn("uploads/file.jpg");
        when(r2StorageService.getSecureUrl(any(), anyBoolean(), anyBoolean())).thenReturn("secure");
        when(r2StorageService.getSecureThumbnailUrl(any(), anyBoolean(), anyBoolean())).thenReturn("secthumb");
        when(userService.findById(userId)).thenReturn(Optional.empty());

        Page<AdminUploadDto> result = uploadService.getUploadsByApprovalStatus(true, pageable);

        verify(uploadRepository).findByApproved(true, pageable);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUuid()).isEqualTo(uploadId);
    }

    @Test
    void getUploadsByFeaturedStatus_mapsToAdminDtoUsingFeaturedQuery() {
        Pageable pageable = PageRequest.of(0, 5);
        Upload upload = newUpload();
        when(uploadRepository.findByFeatured(true, pageable))
                .thenReturn(new PageImpl<>(List.of(upload)));
        when(r2StorageService.extractObjectKeyFromUrl(any())).thenReturn("uploads/file.jpg");
        when(r2StorageService.getSecureUrl(any(), anyBoolean(), anyBoolean())).thenReturn("secure");
        when(r2StorageService.getSecureThumbnailUrl(any(), anyBoolean(), anyBoolean())).thenReturn("secthumb");
        when(userService.findById(userId)).thenReturn(Optional.empty());

        uploadService.getUploadsByFeaturedStatus(true, pageable);

        verify(uploadRepository).findByFeatured(true, pageable);
    }

    @Test
    void getAllUploadsForAdmin_populatesUploaderAndEventInformation() {
        Pageable pageable = PageRequest.of(0, 5);
        UUID eventId = UUID.randomUUID();
        Upload upload = newUpload();
        upload.setEventId(eventId);
        when(uploadRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(upload)));
        when(r2StorageService.extractObjectKeyFromUrl(any())).thenReturn("uploads/file.jpg");
        when(r2StorageService.getSecureUrl(any(), anyBoolean(), anyBoolean())).thenReturn("secure");
        when(r2StorageService.getSecureThumbnailUrl(any(), anyBoolean(), anyBoolean())).thenReturn("secthumb");

        User user = new User();
        user.setFirstName("Ada");
        user.setLastName("Lovelace");
        user.setEmail("ada@example.com");
        when(userService.findById(userId)).thenReturn(Optional.of(user));

        Event event = new Event();
        event.setName("Hackathon");
        when(eventsService.getEventById(eventId)).thenReturn(Optional.of(event));

        Page<AdminUploadDto> result = uploadService.getAllUploadsForAdmin(pageable);

        AdminUploadDto dto = result.getContent().get(0);
        assertThat(dto.getUploaderFirstName()).isEqualTo("Ada");
        assertThat(dto.getUploaderLastName()).isEqualTo("Lovelace");
        assertThat(dto.getEventName()).isEqualTo("Hackathon");
    }

    // ---------- getUserUploadsAsGalleryItems ----------

    @Test
    void getUserUploadsAsGalleryItems_returnsItemsForOwnUploads() {
        Pageable pageable = PageRequest.of(0, 5);
        Upload upload = newUpload();
        upload.setUploadDescription("My photo");
        when(uploadRepository.findByUploadedBy(userId, pageable))
                .thenReturn(new PageImpl<>(List.of(upload)));
        when(r2StorageService.extractObjectKeyFromUrl(any())).thenReturn("uploads/file.jpg");
        when(r2StorageService.getSecureUrl(any(), anyBoolean(), anyBoolean())).thenReturn("secure");
        when(r2StorageService.getSecureThumbnailUrl(any(), anyBoolean(), anyBoolean())).thenReturn("secthumb");

        User user = new User();
        user.setFirstName("Grace");
        user.setLastName("Hopper");
        when(userService.findById(userId)).thenReturn(Optional.of(user));

        Page<GalleryItemDto> result = uploadService.getUserUploadsAsGalleryItems(userId, pageable);

        GalleryItemDto item = result.getContent().get(0);
        assertThat(item.getTitle()).isEqualTo("My photo");
        assertThat(item.getAuthor()).isEqualTo("Grace Hopper");
        assertThat(item.getSrc()).isEqualTo("secure");
    }
}
