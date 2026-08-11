package com.ibrasoft.lensbridge.repository.sql;

import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.PromotableSocialMedia;
import com.ibrasoft.lensbridge.model.board.SocialType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PromotableSocialMediaRepository extends JpaRepository<PromotableSocialMedia, UUID> {

    List<PromotableSocialMedia> findAllByOrderByNameAsc();

    /**
     * Socials a board with this audience should show. Ordered by name so the frame sequence
     * is stable between assemblies — there is no explicit ordering column, and an unordered
     * result would let the same board reshuffle its social frames on every refetch.
     */
    @Query("SELECT s FROM PromotableSocialMedia s WHERE (s.audience = :aud OR s.audience = com.ibrasoft.lensbridge.model.board.Audience.BOTH) ORDER BY s.name ASC")
    List<PromotableSocialMedia> findByAudienceOrBoth(@Param("aud") Audience audience);

    List<PromotableSocialMedia> findByTypeOrderByNameAsc(SocialType type);

    boolean existsByTypeAndUrl(SocialType type, String url);
}