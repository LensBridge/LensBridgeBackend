package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.board.request.CreatePromotableSocialMediaRequest;
import com.ibrasoft.lensbridge.dto.board.request.UpdatePromotableSocialMediaRequest;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.handler.BoardStreamHandler;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.PromotableSocialMedia;
import com.ibrasoft.lensbridge.model.board.SocialType;
import com.ibrasoft.lensbridge.repository.sql.PromotableSocialMediaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotableSocialMediaServiceTest {

    @Mock
    private PromotableSocialMediaRepository repository;

    @Mock
    private BoardStreamHandler boardStream;

    @InjectMocks
    private PromotableSocialMediaService service;

    private PromotableSocialMedia social(String name) {
        return PromotableSocialMedia.builder()
                .id(UUID.randomUUID())
                .name(name)
                .duration(13)
                .audience(Audience.BOTH)
                .type(SocialType.INSTAGRAM)
                .url("https://instagram.com/utmmsa")
                .headerText("follow along")
                .heroText("your home on campus")
                .handle("@utmmsa")
                .footerText("Event recaps, announcements, and more!")
                .build();
    }

    private CreatePromotableSocialMediaRequest.CreatePromotableSocialMediaRequestBuilder createRequest() {
        return CreatePromotableSocialMediaRequest.builder()
                .name("MSA Instagram")
                .duration(13)
                .audience(Audience.BOTH)
                .type(SocialType.INSTAGRAM)
                .url("https://instagram.com/utmmsa")
                .headerText("follow along")
                .heroText("your home on campus")
                .handle("@utmmsa")
                .footerText("Event recaps, announcements, and more!");
    }

    // ==================== Read paths ====================

    @Test
    void getAllDelegatesToRepositorySortedByName() {
        List<PromotableSocialMedia> expected = List.of(social("a"), social("b"));
        when(repository.findAllByOrderByNameAsc()).thenReturn(expected);

        assertThat(service.getAll()).isEqualTo(expected);
    }

    @Test
    void getByIdReturnsEntity() {
        PromotableSocialMedia s = social("a");
        when(repository.findById(s.getId())).thenReturn(Optional.of(s));

        assertThat(service.getById(s.getId())).isSameAs(s);
    }

    @Test
    void getByIdThrowsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(ApiResponseException.class)
                .satisfies(e -> assertThat(((ApiResponseException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getForAudienceFiltersByAudienceOrBoth() {
        List<PromotableSocialMedia> expected = List.of(social("a"));
        when(repository.findByAudienceOrBoth(Audience.SISTERS)).thenReturn(expected);

        assertThat(service.getForAudience(Audience.SISTERS)).isEqualTo(expected);
    }

    @Test
    void getByTypeDelegatesToRepository() {
        List<PromotableSocialMedia> expected = List.of(social("a"));
        when(repository.findByTypeOrderByNameAsc(SocialType.WHATSAPP)).thenReturn(expected);

        assertThat(service.getByType(SocialType.WHATSAPP)).isEqualTo(expected);
    }

    // ==================== create ====================

    @Test
    void createPersistsAllFieldsAndNotifiesBoards() {
        when(repository.save(any(PromotableSocialMedia.class))).thenAnswer(inv -> inv.getArgument(0));

        PromotableSocialMedia created = service.create(createRequest().build());

        assertThat(created.getName()).isEqualTo("MSA Instagram");
        assertThat(created.getType()).isEqualTo(SocialType.INSTAGRAM);
        assertThat(created.getUrl()).isEqualTo("https://instagram.com/utmmsa");
        assertThat(created.getDuration()).isEqualTo(13);
        assertThat(created.getAudience()).isEqualTo(Audience.BOTH);
        assertThat(created.getHandle()).isEqualTo("@utmmsa");
        verify(boardStream).contentChanged("socials");
    }

    @Test
    void createTrimsUrlBeforePersisting() {
        when(repository.save(any(PromotableSocialMedia.class))).thenAnswer(inv -> inv.getArgument(0));

        PromotableSocialMedia created = service.create(
                createRequest().url("  https://instagram.com/utmmsa  ").build());

        assertThat(created.getUrl()).isEqualTo("https://instagram.com/utmmsa");
    }

    @Test
    void createStoresBlankHandleAsNull() {
        when(repository.save(any(PromotableSocialMedia.class))).thenAnswer(inv -> inv.getArgument(0));

        PromotableSocialMedia created = service.create(createRequest().handle("   ").build());

        assertThat(created.getHandle()).isNull();
    }

    @Test
    void createRejectsBlankUrl() {
        assertThatThrownBy(() -> service.create(createRequest().url("   ").build()))
                .isInstanceOf(ApiResponseException.class)
                .satisfies(e -> assertThat(((ApiResponseException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsDuplicateUrlForSameType() {
        when(repository.existsByTypeAndUrl(SocialType.INSTAGRAM, "https://instagram.com/utmmsa"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest().build()))
                .isInstanceOf(ApiResponseException.class)
                .satisfies(e -> assertThat(((ApiResponseException) e).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
        verify(repository, never()).save(any());
    }

    // ==================== update ====================

    @Test
    void updateAppliesOnlyNonNullFields() {
        PromotableSocialMedia existing = social("Old");
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.save(any(PromotableSocialMedia.class))).thenAnswer(inv -> inv.getArgument(0));

        PromotableSocialMedia updated = service.update(existing.getId(),
                UpdatePromotableSocialMediaRequest.builder().heroText("a new home").build());

        assertThat(updated.getHeroText()).isEqualTo("a new home");
        assertThat(updated.getName()).isEqualTo("Old");                    // unchanged
        assertThat(updated.getDuration()).isEqualTo(13);                   // unchanged
        assertThat(updated.getAudience()).isEqualTo(Audience.BOTH);        // unchanged
        assertThat(updated.getHandle()).isEqualTo("@utmmsa");              // unchanged
        verify(boardStream).contentChanged("socials");
    }

    @Test
    void updateWithEmptyHandleClearsIt() {
        PromotableSocialMedia existing = social("Old");
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.save(any(PromotableSocialMedia.class))).thenAnswer(inv -> inv.getArgument(0));

        PromotableSocialMedia updated = service.update(existing.getId(),
                UpdatePromotableSocialMediaRequest.builder().handle("").build());

        assertThat(updated.getHandle()).isNull();
    }

    @Test
    void updateRejectsBlankUrl() {
        PromotableSocialMedia existing = social("Old");
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(existing.getId(),
                UpdatePromotableSocialMediaRequest.builder().url("  ").build()))
                .isInstanceOf(ApiResponseException.class)
                .satisfies(e -> assertThat(((ApiResponseException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(repository, never()).save(any());
    }

    @Test
    void updateThrowsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id,
                UpdatePromotableSocialMediaRequest.builder().build()))
                .isInstanceOf(ApiResponseException.class);
    }

    // ==================== delete ====================

    @Test
    void deleteRemovesEntityAndNotifiesBoards() {
        PromotableSocialMedia existing = social("Old");
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));

        service.delete(existing.getId());

        verify(repository).delete(existing);
        verify(boardStream).contentChanged("socials");
    }

    @Test
    void deleteThrowsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ApiResponseException.class);
        verify(repository, never()).delete(any(PromotableSocialMedia.class));
    }
}
