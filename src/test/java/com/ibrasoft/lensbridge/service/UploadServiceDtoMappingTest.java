package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.upload.response.UploadDto;
import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.minbar.BoardEvent;
import com.ibrasoft.lensbridge.model.upload.Upload;
import com.ibrasoft.lensbridge.model.upload.UploadType;
import com.ibrasoft.lensbridge.repository.sql.BoardEventRepository;
import com.ibrasoft.lensbridge.repository.upload.UploadRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit 2: {@link UploadDto} must never carry a raw R2 object key, and must never de-anonymise
 * an upload whose author asked to stay anonymous.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UploadServiceDtoMappingTest {

    private static final String FILE_KEY = "images/e5c6f37c-845d-4252-9a2f-16e5e68eee96";
    private static final String THUMB_KEY = "thumbnails/e5c6f37c-845d-4252-9a2f-16e5e68eee96";

    private final UUID uploaderId = UUID.randomUUID();
    private final UUID strangerId = UUID.randomUUID();

    @Mock
    private UploadRepository uploadRepository;
    @Mock
    private UserService userService;
    @Mock
    private BoardEventRepository boardEventRepository;
    @Mock
    private R2StorageService r2StorageService;

    private UploadService service;

    @BeforeEach
    void setUp() {
        service = new UploadService(uploadRepository, userService, boardEventRepository, r2StorageService);

        // extractObjectKey is a pass-through for bare keys; mirror that.
        when(r2StorageService.extractObjectKey(any())).thenAnswer(inv -> {
            String v = inv.getArgument(0);
            return (v == null || v.isBlank()) ? null : v;
        });
        when(r2StorageService.getDownloadUrlExpiration()).thenReturn(Duration.ofMinutes(15));

        // Emulate the real approval gate so the test exercises it rather than assuming it.
        when(r2StorageService.getSecureUrl(anyString(), anyBoolean(), anyBoolean()))
                .thenAnswer(inv -> presign(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
        when(r2StorageService.getSecureThumbnailUrl(anyString(), anyBoolean(), anyBoolean()))
                .thenAnswer(inv -> presign(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
    }

    private static String presign(String key, boolean approved, boolean privileged) {
        if (!approved && !privileged) {
            throw new SecurityException("Access denied: content is not approved");
        }
        return "https://media.lensbridge.tech/" + key + "?X-Amz-Expires=900&X-Amz-Signature=deadbeef";
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private User user(UUID id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setFirstName("Test");
        u.setLastName("User");
        return u;
    }

    private Upload anonUpload(boolean approved) {
        Upload upload = new Upload();
        upload.setUuid(UUID.randomUUID());
        upload.setFileName("photo.jpg");
        upload.setFileUrl(FILE_KEY);
        upload.setThumbnailUrl(THUMB_KEY);
        upload.setInstagramHandle("@secret_handle");
        upload.setUploadedBy(user(uploaderId, "uploader@example.com"));
        upload.setBoardEvent(BoardEvent.builder().id(UUID.randomUUID()).name("Jumuah").build());
        upload.setCreatedDate(Instant.now());
        upload.setApproved(approved);
        upload.setAnon(true);
        upload.setContentType(UploadType.IMAGE);
        return upload;
    }

    private void authenticateAs(String email, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null,
                        List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()));
    }

    // ── URL leakage ───────────────────────────────────────────────────────────

    @Test
    void fileAndThumbnailUrlsArePresignedNotRawKeys() {
        UploadDto dto = service.toUploadDto(anonUpload(true),
                new UploadService.UploadViewer(strangerId, false));

        assertThat(dto.getFileUrl()).isNotEqualTo(FILE_KEY);
        assertThat(dto.getThumbnailUrl()).isNotEqualTo(THUMB_KEY);
        assertThat(dto.getFileUrl()).startsWith("https://").contains("X-Amz-Signature=");
        assertThat(dto.getThumbnailUrl()).startsWith("https://").contains("X-Amz-Signature=");
        assertThat(dto.getUrlExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void thumbnailFallsBackToTheFullSizeUrlWhenAbsent() {
        Upload upload = anonUpload(true);
        upload.setThumbnailUrl(null);

        UploadDto dto = service.toUploadDto(upload, new UploadService.UploadViewer(strangerId, false));

        assertThat(dto.getThumbnailUrl()).isEqualTo(dto.getFileUrl());
    }

    @Test
    void unapprovedContentYieldsNoUrlsForUnprivilegedCallersInsteadOfFailing() {
        UploadDto dto = service.toUploadDto(anonUpload(false),
                new UploadService.UploadViewer(strangerId, false));

        assertThat(dto.getFileUrl()).isNull();
        assertThat(dto.getThumbnailUrl()).isNull();
        assertThat(dto.getUrlExpiresAt()).isNull();
        assertThat(dto.getUuid()).isNotNull();
    }

    @Test
    void unapprovedContentIsViewableByItsOwnUploader() {
        UploadDto dto = service.toUploadDto(anonUpload(false),
                new UploadService.UploadViewer(uploaderId, false));

        assertThat(dto.getFileUrl()).contains("X-Amz-Signature=");
    }

    @Test
    void unapprovedContentIsViewableByModerators() {
        UploadDto dto = service.toUploadDto(anonUpload(false),
                new UploadService.UploadViewer(strangerId, true));

        assertThat(dto.getFileUrl()).contains("X-Amz-Signature=");
    }

    // ── Anonymity ─────────────────────────────────────────────────────────────

    @Test
    void anonymousUploadHidesUploaderAndHandleFromOtherUsers() {
        UploadDto dto = service.toUploadDto(anonUpload(true),
                new UploadService.UploadViewer(strangerId, false));

        assertThat(dto.isAnon()).isTrue();
        assertThat(dto.getUploadedBy()).isNull();
        assertThat(dto.getInstagramHandle()).isNull();
    }

    @Test
    void anonymousUploadHidesUploaderFromUnauthenticatedCallers() {
        UploadDto dto = service.toUploadDto(anonUpload(true), UploadService.UploadViewer.anonymous());

        assertThat(dto.getUploadedBy()).isNull();
        assertThat(dto.getInstagramHandle()).isNull();
    }

    @Test
    void anonymousUploadIsUnmaskedForTheUploaderThemselves() {
        UploadDto dto = service.toUploadDto(anonUpload(true),
                new UploadService.UploadViewer(uploaderId, false));

        assertThat(dto.getUploadedBy()).isEqualTo(uploaderId);
        assertThat(dto.getInstagramHandle()).isEqualTo("@secret_handle");
    }

    @Test
    void anonymousUploadIsUnmaskedForHoldersOfMediaUploadRead() {
        UploadDto dto = service.toUploadDto(anonUpload(true),
                new UploadService.UploadViewer(strangerId, true));

        assertThat(dto.getUploadedBy()).isEqualTo(uploaderId);
        assertThat(dto.getInstagramHandle()).isEqualTo("@secret_handle");
    }

    @Test
    void nonAnonymousUploadKeepsItsAttribution() {
        Upload upload = anonUpload(true);
        upload.setAnon(false);

        UploadDto dto = service.toUploadDto(upload, new UploadService.UploadViewer(strangerId, false));

        assertThat(dto.getUploadedBy()).isEqualTo(uploaderId);
        assertThat(dto.getInstagramHandle()).isEqualTo("@secret_handle");
    }

    // ── Viewer resolution ─────────────────────────────────────────────────────

    @Test
    void viewerIsAnonymousWithNoAuthentication() {
        SecurityContextHolder.clearContext();

        UploadService.UploadViewer viewer = service.currentViewer();

        assertThat(viewer.userId()).isNull();
        assertThat(viewer.canReadAllUploads()).isFalse();
    }

    @Test
    void viewerIsAnonymousForTheAnonymousToken() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThat(service.currentViewer().userId()).isNull();
    }

    @Test
    void viewerResolvesIdAndModeratorAuthority() {
        when(userService.findByEmail("mod@example.com"))
                .thenReturn(Optional.of(user(strangerId, "mod@example.com")));
        authenticateAs("mod@example.com", Permission.Authority.MEDIA_UPLOAD_READ);

        UploadService.UploadViewer viewer = service.currentViewer();

        assertThat(viewer.userId()).isEqualTo(strangerId);
        assertThat(viewer.canReadAllUploads()).isTrue();
    }

    @Test
    void plainUserIsNotAMediaModerator() {
        when(userService.findByEmail("user@example.com"))
                .thenReturn(Optional.of(user(uploaderId, "user@example.com")));
        authenticateAs("user@example.com", "ROLE_USER", Permission.Authority.MEDIA_UPLOAD_SELF);

        UploadService.UploadViewer viewer = service.currentViewer();

        assertThat(viewer.userId()).isEqualTo(uploaderId);
        assertThat(viewer.canReadAllUploads()).isFalse();
    }

    @Test
    void unresolvablePrincipalDegradesToLeastPrivilege() {
        when(userService.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        authenticateAs("ghost@example.com", "ROLE_USER");

        UploadService.UploadViewer viewer = service.currentViewer();

        assertThat(viewer.userId()).isNull();
        assertThat(viewer.canReadAllUploads()).isFalse();
        // …and a null-id viewer must not accidentally match a null-uploader upload.
        Upload orphan = anonUpload(true);
        orphan.setUploadedBy(null);
        assertThat(viewer.isUploader(orphan)).isFalse();
        assertThat(service.toUploadDto(orphan, viewer).getUploadedBy()).isNull();
    }
}
