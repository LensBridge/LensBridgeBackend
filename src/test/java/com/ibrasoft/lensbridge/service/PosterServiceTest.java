package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.board.request.CreatePosterRequest;
import com.ibrasoft.lensbridge.dto.board.request.UpdatePosterRequest;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Poster;
import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.model.board.frames.FrameType;
import com.ibrasoft.lensbridge.repository.sql.PosterRepository;
import com.ibrasoft.lensbridge.service.board.transformer.PosterFrameTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PosterServiceTest {

    @Mock
    private PosterRepository posterRepository;

    @Mock
    private R2StorageService r2StorageService;

    @Mock
    private PosterFrameTransformer posterFrameTransformer;

    @InjectMocks
    private PosterService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "publicUrl", "https://cdn.example.com");
    }

    private Poster poster(String title) {
        return Poster.builder()
                .id(UUID.randomUUID())
                .title(title)
                .image("https://cdn.example.com/poster-old.png")
                .duration(10)
                .startTime(Instant.now())
                .endTime(Instant.now().plusSeconds(3600))
                .audience(Audience.BOTH)
                .build();
    }

    private MockMultipartFile imageFile() {
        return new MockMultipartFile("file", "pic.png", "image/png", new byte[]{1, 2, 3});
    }

    // ==================== Read paths ====================

    @Test
    void getAllPostersDelegatesToRepositorySortedByStartTimeDesc() {
        List<Poster> expected = List.of(poster("a"), poster("b"));
        when(posterRepository.findAllByOrderByStartTimeDesc()).thenReturn(expected);

        assertThat(service.getAllPosters()).isEqualTo(expected);
    }

    @Test
    void getPosterByIdReturnsPoster() {
        Poster p = poster("a");
        when(posterRepository.findById(p.getId())).thenReturn(Optional.of(p));

        assertThat(service.getPosterById(p.getId())).isSameAs(p);
    }

    @Test
    void getPosterByIdThrowsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(posterRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPosterById(id))
                .isInstanceOf(ApiResponseException.class)
                .satisfies(e -> assertThat(((ApiResponseException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getPostersForAudienceFiltersByAudienceOrBoth() {
        List<Poster> expected = List.of(poster("a"));
        when(posterRepository.findByAudienceOrBoth(Audience.SISTERS)).thenReturn(expected);

        assertThat(service.getPostersForAudience(Audience.SISTERS)).isEqualTo(expected);
    }

    @Test
    void getActivePosterFramesForAudienceQueriesActiveForAudience() {
        List<Poster> expected = List.of(poster("a"));
        when(posterRepository.findActivePostersForAudienceAt(any(Instant.class), any(Audience.class)))
                .thenReturn(expected);

        assertThat(service.getActivePosterFramesForAudience(Audience.BROTHERS)).isEqualTo(expected);
    }

    @Test
    void getActivePostersQueriesActiveAtNow() {
        List<Poster> expected = List.of(poster("a"));
        when(posterRepository.findActivePostersAt(any(Instant.class))).thenReturn(expected);

        assertThat(service.getActivePosters()).isEqualTo(expected);
    }

    @Test
    void getActivePosterFrameDefinitionsMapsThroughTransformer() {
        Poster p = poster("a");
        FrameDefinition frame = FrameDefinition.builder().frameType(FrameType.POSTER).build();
        when(posterRepository.findActivePostersForAudienceAt(any(Instant.class), any(Audience.class)))
                .thenReturn(List.of(p));
        when(posterFrameTransformer.transform(p, null)).thenReturn(frame);

        List<FrameDefinition> result = service.getActivePosterFrameDefinitions(Audience.BOTH);

        assertThat(result).containsExactly(frame);
    }

    // ==================== createPoster ====================

    @Test
    void createPosterUploadsImageAndPersists() throws IOException {
        CreatePosterRequest request = CreatePosterRequest.builder()
                .title("New")
                .duration(15)
                .startTime(Instant.now())
                .endTime(Instant.now().plusSeconds(7200))
                .audience(Audience.BROTHERS)
                .build();
        when(r2StorageService.uploadImage(anyString(), any(MultipartFile.class)))
                .thenReturn("poster-key.png");
        when(posterRepository.save(any(Poster.class))).thenAnswer(inv -> inv.getArgument(0));

        Poster created = service.createPoster(request, imageFile());

        assertThat(created.getTitle()).isEqualTo("New");
        assertThat(created.getImage()).isEqualTo("https://cdn.example.com/poster-key.png");
        assertThat(created.getDuration()).isEqualTo(15);
        assertThat(created.getAudience()).isEqualTo(Audience.BROTHERS);
    }

    @Test
    void createPosterRejectsInvalidDateRange() {
        Instant start = Instant.now();
        CreatePosterRequest request = CreatePosterRequest.builder()
                .title("X")
                .duration(5)
                .startTime(start)
                .endTime(start) // not after start
                .audience(Audience.BOTH)
                .build();

        assertThatThrownBy(() -> service.createPoster(request, imageFile()))
                .isInstanceOf(ApiResponseException.class)
                .satisfies(e -> assertThat(((ApiResponseException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(posterRepository, never()).save(any());
    }

    @Test
    void createPosterRejectsMissingImage() {
        CreatePosterRequest request = CreatePosterRequest.builder()
                .title("X")
                .duration(5)
                .startTime(Instant.now())
                .endTime(Instant.now().plusSeconds(60))
                .audience(Audience.BOTH)
                .build();
        MultipartFile empty = new MockMultipartFile("file", new byte[0]);

        assertThatThrownBy(() -> service.createPoster(request, empty))
                .isInstanceOf(ApiResponseException.class)
                .satisfies(e -> assertThat(((ApiResponseException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createPosterRejectsNonImageContentType() {
        CreatePosterRequest request = CreatePosterRequest.builder()
                .title("X")
                .duration(5)
                .startTime(Instant.now())
                .endTime(Instant.now().plusSeconds(60))
                .audience(Audience.BOTH)
                .build();
        MultipartFile pdf = new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[]{1});

        assertThatThrownBy(() -> service.createPoster(request, pdf))
                .isInstanceOf(ApiResponseException.class);
    }

    @Test
    void createPosterWrapsUploadIoExceptionAsServerError() throws IOException {
        CreatePosterRequest request = CreatePosterRequest.builder()
                .title("X")
                .duration(5)
                .startTime(Instant.now())
                .endTime(Instant.now().plusSeconds(60))
                .audience(Audience.BOTH)
                .build();
        when(r2StorageService.uploadImage(anyString(), any(MultipartFile.class)))
                .thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> service.createPoster(request, imageFile()))
                .isInstanceOf(ApiResponseException.class)
                .satisfies(e -> assertThat(((ApiResponseException) e).getStatus())
                        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    // ==================== updatePoster ====================

    @Test
    void updatePosterAppliesOnlyNonNullFields() {
        Poster existing = poster("Old");
        when(posterRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(posterRepository.save(any(Poster.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdatePosterRequest request = UpdatePosterRequest.builder()
                .title("Updated")
                .build();

        Poster updated = service.updatePoster(existing.getId(), request);

        assertThat(updated.getTitle()).isEqualTo("Updated");
        assertThat(updated.getDuration()).isEqualTo(10); // unchanged
        assertThat(updated.getAudience()).isEqualTo(Audience.BOTH); // unchanged
    }

    @Test
    void updatePosterThrowsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(posterRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePoster(id, UpdatePosterRequest.builder().build()))
                .isInstanceOf(ApiResponseException.class);
    }

    @Test
    void updatePosterValidatesResultingDateRange() {
        Poster existing = poster("Old");
        Instant start = existing.getStartTime();
        when(posterRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        UpdatePosterRequest request = UpdatePosterRequest.builder()
                .endTime(start.minusSeconds(10)) // end before start
                .build();

        assertThatThrownBy(() -> service.updatePoster(existing.getId(), request))
                .isInstanceOf(ApiResponseException.class)
                .satisfies(e -> assertThat(((ApiResponseException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(posterRepository, never()).save(any());
    }

    // ==================== updatePosterImage ====================

    @Test
    void updatePosterImageDeletesOldAndUploadsNew() throws IOException {
        Poster existing = poster("Old");
        when(posterRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(r2StorageService.uploadImage(anyString(), any(MultipartFile.class)))
                .thenReturn("new-key.png");
        when(posterRepository.save(any(Poster.class))).thenAnswer(inv -> inv.getArgument(0));

        Poster updated = service.updatePosterImage(existing.getId(), imageFile());

        verify(r2StorageService).deleteObject("https://cdn.example.com/poster-old.png");
        assertThat(updated.getImage()).isEqualTo("https://cdn.example.com/new-key.png");
    }

    @Test
    void updatePosterImageThrowsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(posterRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePosterImage(id, imageFile()))
                .isInstanceOf(ApiResponseException.class);
    }

    // ==================== deletePoster ====================

    @Test
    void deletePosterRemovesImageAndEntity() {
        Poster existing = poster("Old");
        when(posterRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        service.deletePoster(existing.getId());

        verify(r2StorageService).deleteObject("https://cdn.example.com/poster-old.png");
        verify(posterRepository).delete(existing);
    }

    @Test
    void deletePosterStillDeletesEntityWhenImageRemovalFails() {
        Poster existing = poster("Old");
        when(posterRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        org.mockito.Mockito.doThrow(new RuntimeException("r2 down"))
                .when(r2StorageService).deleteObject(anyString());

        service.deletePoster(existing.getId());

        verify(posterRepository).delete(existing);
    }

    @Test
    void deletePosterThrowsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(posterRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePoster(id))
                .isInstanceOf(ApiResponseException.class);
    }
}
