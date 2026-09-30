package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.upload.response.UploadDto;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.minbar.BoardEvent;
import com.ibrasoft.lensbridge.model.upload.Upload;
import com.ibrasoft.lensbridge.repository.sql.BoardEventRepository;
import com.ibrasoft.lensbridge.repository.upload.UploadRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Read-path authorization for {@link UploadService}.
 *
 * <p>Before this was enforced, {@code getUploadByIdAsDto} was a bare {@code findById} and
 * {@code getUploadsByEventAsDto} had no approval filter, so any signed-in user could read any
 * upload by UUID and could page an event's entire pending/rejected moderation queue. The rule
 * these tests pin down: an unprivileged viewer sees approved uploads plus their own, nothing else.
 */
@ExtendWith(MockitoExtension.class)
class UploadServiceVisibilityTest {

    private static final UUID VIEWER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID UPLOAD_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID EVENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Mock
    private UploadRepository uploadRepository;
    @Mock
    private UserService userService;
    @Mock
    private BoardEventRepository boardEventRepository;
    @Mock
    private R2StorageService r2StorageService;

    @InjectMocks
    private UploadService uploadService;

    private static User user(UUID id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static Upload upload(UUID ownerId, boolean approved) {
        Upload upload = new Upload();
        upload.setUuid(UPLOAD_ID);
        upload.setFileName("shot.jpg");
        upload.setFileUrl("uploads/shot.jpg");
        upload.setUploadedBy(user(ownerId));
        upload.setApproved(approved);
        upload.setCreatedDate(Instant.now());
        return upload;
    }

    @Nested
    class GetUploadByIdAsDto {

        @Test
        void hidesSomeoneElsesUnapprovedUploadFromAnOrdinaryViewer() {
            when(uploadRepository.findById(UPLOAD_ID)).thenReturn(Optional.of(upload(OTHER_USER_ID, false)));

            Optional<UploadDto> result = uploadService.getUploadByIdAsDto(UPLOAD_ID, VIEWER_ID, false);

            assertThat(result).isEmpty();
        }

        @Test
        void showsSomeoneElsesApprovedUploadToAnOrdinaryViewer() {
            when(uploadRepository.findById(UPLOAD_ID)).thenReturn(Optional.of(upload(OTHER_USER_ID, true)));

            Optional<UploadDto> result = uploadService.getUploadByIdAsDto(UPLOAD_ID, VIEWER_ID, false);

            assertThat(result).isPresent();
            assertThat(result.get().getUuid()).isEqualTo(UPLOAD_ID);
        }

        @Test
        void showsTheViewerTheirOwnUploadWhileItIsStillPending() {
            when(uploadRepository.findById(UPLOAD_ID)).thenReturn(Optional.of(upload(VIEWER_ID, false)));

            Optional<UploadDto> result = uploadService.getUploadByIdAsDto(UPLOAD_ID, VIEWER_ID, false);

            assertThat(result).isPresent();
            assertThat(result.get().isApproved()).isFalse();
        }

        @Test
        void showsAnyUnapprovedUploadToAModerator() {
            when(uploadRepository.findById(UPLOAD_ID)).thenReturn(Optional.of(upload(OTHER_USER_ID, false)));

            Optional<UploadDto> result = uploadService.getUploadByIdAsDto(UPLOAD_ID, VIEWER_ID, true);

            assertThat(result).isPresent();
        }

        @Test
        void hidesSoftDeletedUploadsEvenFromTheirOwnerAndFromModerators() {
            Upload deleted = upload(VIEWER_ID, true);
            deleted.setDeletedAt(Instant.now());
            when(uploadRepository.findById(UPLOAD_ID)).thenReturn(Optional.of(deleted));

            assertThat(uploadService.getUploadByIdAsDto(UPLOAD_ID, VIEWER_ID, false)).isEmpty();
            assertThat(uploadService.getUploadByIdAsDto(UPLOAD_ID, VIEWER_ID, true)).isEmpty();
        }

        @Test
        void returnsEmptyForAnUnknownId() {
            when(uploadRepository.findById(UPLOAD_ID)).thenReturn(Optional.empty());

            assertThat(uploadService.getUploadByIdAsDto(UPLOAD_ID, VIEWER_ID, false)).isEmpty();
        }
    }

    @Nested
    class GetUploadsByEventAsDto {

        private final Pageable pageable = PageRequest.of(0, 20);

        private BoardEvent stubEvent() {
            BoardEvent event = new BoardEvent();
            event.setId(EVENT_ID);
            event.setName("Halaqa Night");
            when(boardEventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
            return event;
        }

        @Test
        void restrictsAnOrdinaryViewerToApprovedUploadsAndTheirOwn() {
            BoardEvent event = stubEvent();
            Page<Upload> page = new PageImpl<>(List.of(upload(VIEWER_ID, false)));
            when(uploadRepository.findVisibleByBoardEvent(event, VIEWER_ID, pageable)).thenReturn(page);

            Page<UploadDto> result = uploadService.getUploadsByEventAsDto(EVENT_ID, pageable, VIEWER_ID, false);

            assertThat(result.getContent()).hasSize(1);
            verify(uploadRepository, never()).findByBoardEventAndDeletedAtIsNull(any(), any());
        }

        @Test
        void givesAModeratorTheWholeQueue() {
            BoardEvent event = stubEvent();
            Page<Upload> page = new PageImpl<>(List.of(upload(OTHER_USER_ID, false)));
            when(uploadRepository.findByBoardEventAndDeletedAtIsNull(event, pageable)).thenReturn(page);

            Page<UploadDto> result = uploadService.getUploadsByEventAsDto(EVENT_ID, pageable, VIEWER_ID, true);

            assertThat(result.getContent()).hasSize(1);
            verify(uploadRepository, never()).findVisibleByBoardEvent(any(), any(), any());
        }

        @Test
        void stillRejectsAnUnknownEvent() {
            when(boardEventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> uploadService.getUploadsByEventAsDto(EVENT_ID, pageable, VIEWER_ID, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Event not found");
        }
    }
}
