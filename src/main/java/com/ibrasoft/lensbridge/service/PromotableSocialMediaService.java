package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.board.request.CreatePromotableSocialMediaRequest;
import com.ibrasoft.lensbridge.dto.board.request.UpdatePromotableSocialMediaRequest;
import com.ibrasoft.lensbridge.dto.upload.response.ErrorResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.handler.BoardStreamHandler;
import com.ibrasoft.lensbridge.model.minbar.Audience;
import com.ibrasoft.lensbridge.model.minbar.board.PromotableSocialMedia;
import com.ibrasoft.lensbridge.model.minbar.board.SocialType;
import com.ibrasoft.lensbridge.repository.sql.PromotableSocialMediaRepository;
import com.ibrasoft.lensbridge.util.Patch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * CRUD for the social accounts a board can promote. Unlike posters these have no viewing
 * window — a social page is either promoted or it isn't — so the only filter that matters
 * for assembly is audience.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromotableSocialMediaService {

    private final PromotableSocialMediaRepository repository;
    private final BoardStreamHandler boardStream;

    /** Every promotable social, ordered by name. */
    public List<PromotableSocialMedia> getAll() {
        return repository.findAllByOrderByNameAsc();
    }

    public PromotableSocialMedia getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiResponseException(
                        HttpStatus.NOT_FOUND,
                        ErrorResponse.of("Promotable social media not found with id: " + id)));
    }

    /**
     * Socials a board should show: those targeting this audience plus those targeting BOTH.
     * This is what the socials frame producer consumes.
     */
    public List<PromotableSocialMedia> getForAudience(Audience audience) {
        return repository.findByAudienceOrBoth(audience);
    }

    public List<PromotableSocialMedia> getByType(SocialType type) {
        return repository.findByTypeOrderByNameAsc(type);
    }

    public PromotableSocialMedia create(CreatePromotableSocialMediaRequest request) {
        String url = normalize(request.getUrl());
        if (repository.existsByTypeAndUrl(request.getType(), url)) {
            throw new ApiResponseException(
                    HttpStatus.CONFLICT,
                    ErrorResponse.of("That " + request.getType() + " URL is already promoted"));
        }

        PromotableSocialMedia social = PromotableSocialMedia.builder()
                .name(request.getName())
                .duration(request.getDuration())
                .audience(request.getAudience())
                .type(request.getType())
                .url(url)
                .headerText(request.getHeaderText())
                .heroText(request.getHeroText())
                .handle(blankToNull(request.getHandle()))
                .footerText(request.getFooterText())
                .build();

        social = repository.save(social);
        log.info("Created promotable social media: id={}, type={}, name={}",
                social.getId(), social.getType(), social.getName());
        boardStream.contentChanged("socials");

        return social;
    }

    public PromotableSocialMedia update(UUID id, UpdatePromotableSocialMediaRequest request) {
        PromotableSocialMedia social = getById(id);

        Patch.apply(request.getName(), social::setName);
        Patch.apply(request.getDuration(), social::setDuration);
        Patch.apply(request.getAudience(), social::setAudience);
        Patch.apply(request.getType(), social::setType);
        Patch.apply(request.getHeaderText(), social::setHeaderText);
        Patch.apply(request.getHeroText(), social::setHeroText);
        Patch.apply(request.getFooterText(), social::setFooterText);

        if (request.getUrl() != null) {
            social.setUrl(normalize(request.getUrl()));
        }
        // Not Patch.apply: an empty string means "clear the handle", which the null-guard
        // there would let through unchanged.
        if (request.getHandle() != null) {
            social.setHandle(blankToNull(request.getHandle()));
        }

        social = repository.save(social);
        log.info("Updated promotable social media: id={}", id);
        boardStream.contentChanged("socials");

        return social;
    }

    public void delete(UUID id) {
        PromotableSocialMedia social = getById(id);

        repository.delete(social);
        log.info("Deleted promotable social media: id={}", id);
        boardStream.contentChanged("socials");
    }

    // ==================== Helper Methods ====================

    private static String normalize(String url) {
        String trimmed = url == null ? "" : url.trim();
        if (trimmed.isEmpty()) {
            throw new ApiResponseException(
                    HttpStatus.BAD_REQUEST,
                    ErrorResponse.of("URL is required"));
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
