package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.upload.response.UploadDto;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.exception.FileProcessingException;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.upload.MediaEvent;
import com.ibrasoft.lensbridge.model.upload.Upload;
import com.ibrasoft.lensbridge.model.upload.UploadType;
import com.ibrasoft.lensbridge.repository.upload.UploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadServiceTest {

    private final UploadRepository uploadRepository = mock(UploadRepository.class);
    private final UserService userService = mock(UserService.class);
    private final EventsService eventsService = mock(EventsService.class);
    private final R2StorageService r2 = mock(R2StorageService.class);

    private UploadService service;
    private User owner;
    private MediaEvent event;

    @BeforeEach
    void setUp() {
        service = new UploadService(uploadRepository, userService, eventsService, r2);
        owner = new User();
        owner.setId(UUID.randomUUID());
        event = MediaEvent.builder().id(UUID.randomUUID()).name("Iftar").date(Instant.now()).build();
        when(userService.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(eventsService.getEventById(event.getId())).thenReturn(Optional.of(event));
    }

    private Upload uploadBy(User uploader, boolean anon) {
        Upload upload = new Upload();
        upload.setUuid(UUID.randomUUID());
        upload.setFileUrl("images/" + upload.getUuid());
        upload.setUploadedBy(uploader);
        upload.setMediaEvent(event);
        upload.setAnon(anon);
        upload.setContentType(UploadType.IMAGE);
        return upload;
    }

    // ── createUpload ──────────────────────────────────────────────────────────

    @Test
    void createUploadSavesTheRecord() {
        Upload upload = service.createUpload("images/k", "a.jpg", event.getId(), "d", "@h", true, owner.getId());

        assertThat(upload.getFileUrl()).isEqualTo("images/k");
        assertThat(upload.getUploadedBy()).isSameAs(owner);
        assertThat(upload.isAnon()).isTrue();
        verify(uploadRepository).save(upload);
        verify(r2, never()).deleteObject(any());
    }

    @Test
    void aMissingEventIsA404NotA500AndTheObjectIsNotLeftBehind() {
        UUID unknown = UUID.randomUUID();
        when(eventsService.getEventById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createUpload("images/k", "a.jpg", unknown, null, null, false, owner.getId()))
                .isInstanceOfSatisfying(ApiResponseException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(r2).deleteObject("images/k");
    }

    @Test
    void aMissingUserIsA404() {
        UUID unknown = UUID.randomUUID();
        when(userService.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createUpload("images/k", "a.jpg", event.getId(), null, null, false, unknown))
                .isInstanceOfSatisfying(ApiResponseException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void aFailureToPersistDeletesTheUploadedObject() {
        doThrow(new IllegalStateException("db down")).when(uploadRepository).save(any());

        assertThatThrownBy(() -> service.createUpload("images/k", "a.jpg", event.getId(), null, null, false, owner.getId()))
                .isInstanceOf(FileProcessingException.class);
        verify(r2).deleteObject("images/k");
    }

    @Test
    void aFailureToDeleteTheOrphanDoesNotMaskTheOriginalError() {
        doThrow(new IllegalStateException("db down")).when(uploadRepository).save(any());
        doThrow(new RuntimeException("r2 down")).when(r2).deleteObject("images/k");

        assertThatThrownBy(() -> service.createUpload("images/k", "a.jpg", event.getId(), null, null, false, owner.getId()))
                .isInstanceOf(FileProcessingException.class);
    }

    // ── moderator delete ──────────────────────────────────────────────────────

    @Test
    void moderatorDeleteStampsDeletedAtAndDeletedBy() {
        User admin = new User();
        admin.setId(UUID.randomUUID());
        when(userService.findById(admin.getId())).thenReturn(Optional.of(admin));
        Upload upload = uploadBy(owner, false);
        when(uploadRepository.findById(upload.getUuid())).thenReturn(Optional.of(upload));
        when(r2.extractObjectKey(upload.getFileUrl())).thenReturn(upload.getFileUrl());

        service.deleteUpload(upload.getUuid(), admin.getId());

        assertThat(upload.getDeletedAt()).isNotNull();
        assertThat(upload.getDeletedBy()).isSameAs(admin);
        verify(r2).deleteObject(upload.getFileUrl());
        verify(uploadRepository).save(upload);
    }

    @Test
    void deletingAnAlreadyDeletedUploadChangesNothing() {
        Instant firstDeleted = Instant.parse("2026-01-01T00:00:00Z");
        Upload upload = uploadBy(owner, false);
        upload.setDeletedAt(firstDeleted);
        when(uploadRepository.findById(upload.getUuid())).thenReturn(Optional.of(upload));

        service.deleteUpload(upload.getUuid(), UUID.randomUUID());

        assertThat(upload.getDeletedAt()).isEqualTo(firstDeleted);
        assertThat(upload.getDeletedBy()).isNull();
        verify(r2, never()).deleteObject(any());
        verify(uploadRepository, never()).save(any());
    }

    @Test
    void deletingAnUnknownUploadIsStillAnError() {
        UUID unknown = UUID.randomUUID();
        when(uploadRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteUpload(unknown, null)).isInstanceOf(IllegalArgumentException.class);
    }

    // ── reading an upload ─────────────────────────────────────────────────────

    @Test
    void theOwnerCanReadTheirUpload() {
        Upload upload = uploadBy(owner, true);
        when(uploadRepository.findByUuidAndDeletedAtIsNull(upload.getUuid())).thenReturn(upload);

        Optional<UploadDto> dto = service.getUploadByIdAsDto(upload.getUuid(), owner.getId(), false);

        assertThat(dto).isPresent();
        assertThat(dto.get().getUuid()).isEqualTo(upload.getUuid());
    }

    @Test
    void aModeratorCanReadAnyonesUpload() {
        Upload upload = uploadBy(owner, true);
        when(uploadRepository.findByUuidAndDeletedAtIsNull(upload.getUuid())).thenReturn(upload);

        assertThat(service.getUploadByIdAsDto(upload.getUuid(), UUID.randomUUID(), true)).isPresent();
    }

    @Test
    void anotherUserGetsNothingSoTheCallerAnswers404() {
        Upload upload = uploadBy(owner, false);
        when(uploadRepository.findByUuidAndDeletedAtIsNull(upload.getUuid())).thenReturn(upload);

        assertThat(service.getUploadByIdAsDto(upload.getUuid(), UUID.randomUUID(), false)).isEmpty();
    }

    @Test
    void aSoftDeletedUploadIsInvisibleEvenToItsOwnerAndModerators() {
        UUID id = UUID.randomUUID();
        when(uploadRepository.findByUuidAndDeletedAtIsNull(id)).thenReturn(null);

        assertThat(service.getUploadByIdAsDto(id, owner.getId(), false)).isEmpty();
        assertThat(service.getUploadByIdAsDto(id, owner.getId(), true)).isEmpty();
    }

    // ── listing by event ──────────────────────────────────────────────────────

    @Test
    void aModeratorListsEveryUploadForTheEvent() {
        Pageable pageable = PageRequest.of(0, 10);
        Upload someonesElse = uploadBy(new User(), false);
        when(uploadRepository.findByMediaEventAndDeletedAtIsNull(event, pageable))
                .thenReturn(new PageImpl<>(List.of(someonesElse)));

        assertThat(service.getUploadsByEventAsDto(event.getId(), UUID.randomUUID(), true, pageable))
                .hasSize(1);
    }

    @Test
    void everyoneElseListsOnlyTheirOwnUploadsForTheEvent() {
        Pageable pageable = PageRequest.of(0, 10);
        Upload mine = uploadBy(owner, true);
        when(uploadRepository.findByMediaEventAndUploadedByAndDeletedAtIsNull(event, owner, pageable))
                .thenReturn(new PageImpl<>(List.of(mine)));

        assertThat(service.getUploadsByEventAsDto(event.getId(), owner.getId(), false, pageable))
                .extracting(UploadDto::getUuid).containsExactly(mine.getUuid());
        verify(uploadRepository, never()).findByMediaEventAndDeletedAtIsNull(any(), any());
    }
}
