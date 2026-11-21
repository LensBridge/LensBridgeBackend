package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.upload.response.GalleryItemDto;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.upload.Upload;
import com.ibrasoft.lensbridge.model.upload.UploadType;
import com.ibrasoft.lensbridge.repository.upload.EventsRepository;
import com.ibrasoft.lensbridge.repository.upload.UploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GalleryServiceTest {

    private final UploadRepository uploadRepository = mock(UploadRepository.class);
    private final R2StorageService r2 = mock(R2StorageService.class);
    private GalleryService service;

    @BeforeEach
    void setUp() {
        service = new GalleryService(uploadRepository, mock(UserService.class), mock(EventsRepository.class), r2);
        when(uploadRepository.findByApprovedTrueAndDeletedAtIsNull(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
    }

    private Pageable queriedPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(uploadRepository).findByApprovedTrueAndDeletedAtIsNull(captor.capture());
        return captor.getValue();
    }

    @Test
    void sortingByAWhitelistedPropertyIsPassedThrough() {
        service.getAllApprovedGalleryItems(PageRequest.of(2, 20, Sort.by(Sort.Direction.DESC, "createdDate")
                .and(Sort.by("featured"))));

        Pageable used = queriedPageable();
        assertThat(used.getPageNumber()).isEqualTo(2);
        assertThat(used.getPageSize()).isEqualTo(20);
        assertThat(used.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdDate").and(Sort.by("featured")));
    }

    @Test
    void sortingByAnythingElseIsRefusedBeforeTheDatabaseIsAsked() {
        for (String property : new String[] { "uploadedBy.email", "approved", "nonexistent" }) {
            assertThatThrownBy(() -> service.getAllApprovedGalleryItems(PageRequest.of(0, 10, Sort.by(property))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(property);
        }
        verify(uploadRepository, never()).findByApprovedTrueAndDeletedAtIsNull(any());
    }

    @Test
    void thePageSizeIsCapped() {
        service.getAllApprovedGalleryItems(PageRequest.of(1, 5000));

        assertThat(queriedPageable().getPageSize()).isEqualTo(GalleryService.MAX_PAGE_SIZE);
        assertThat(queriedPageable().getPageNumber()).isEqualTo(1);
    }

    @Test
    void anUnpagedRequestBecomesAFirstCappedPage() {
        service.getAllApprovedGalleryItems(Pageable.unpaged());

        assertThat(queriedPageable().getPageSize()).isEqualTo(GalleryService.MAX_PAGE_SIZE);
    }

    @Test
    void anUploadWhoseEventLinkIsGoneStillRendersInsteadOfFailingThePage() {
        User uploader = new User();
        uploader.setFirstName("A");
        uploader.setLastName("B");
        Upload upload = new Upload();
        upload.setUuid(UUID.randomUUID());
        upload.setFileUrl("images/k");
        upload.setUploadedBy(uploader);
        upload.setMediaEvent(null);
        upload.setApproved(true);
        upload.setContentType(UploadType.IMAGE);
        upload.setCreatedDate(Instant.parse("2026-10-06T12:00:00Z"));
        when(uploadRepository.findByApprovedTrueAndDeletedAtIsNull(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(upload)));
        when(r2.getSecureUrlForStoredFile("images/k", true, false)).thenReturn("https://signed/k");
        when(r2.getSecureThumbnailUrlOrOriginal("images/k", null, true, false)).thenReturn("https://signed/k");

        GalleryItemDto item = service.getAllApprovedGalleryItems(PageRequest.of(0, 10)).getContent().get(0);

        assertThat(item.getEvent()).isEqualTo("Unknown");
        assertThat(item.getSrc()).isEqualTo("https://signed/k");
        assertThat(item.getAuthor()).isEqualTo("A B");
    }
}
