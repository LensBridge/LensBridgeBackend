package com.ibrasoft.lensbridge.repository.sql;

import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Poster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface PosterRepository extends JpaRepository<Poster, UUID> {

    List<Poster> findAllByOrderByStartTimeDesc();

    @Query("SELECT p FROM Poster p WHERE (p.audience = :aud OR p.audience = com.ibrasoft.lensbridge.model.board.Audience.BOTH) ORDER BY p.startTime DESC")
    List<Poster> findByAudienceOrBoth(@Param("aud") Audience audience);

    @Query("SELECT p FROM Poster p WHERE (p.audience = :aud OR p.audience = com.ibrasoft.lensbridge.model.board.Audience.BOTH) AND p.startTime <= :now AND p.endTime > :now ORDER BY p.startTime DESC")
    List<Poster> findActivePostersForAudienceAt(@Param("now") Instant now, @Param("aud") Audience audience);

    /**
     * Posters for the audience (or BOTH) active at any point in {@code [from, to)}: their
     * {@code [startTime, endTime)} overlaps it. Used for offline bundle days; the live board
     * uses {@link #findActivePostersForAudienceAt}.
     */
    @Query("SELECT p FROM Poster p WHERE (p.audience = :aud OR p.audience = com.ibrasoft.lensbridge.model.board.Audience.BOTH) AND p.startTime < :to AND p.endTime > :from ORDER BY p.startTime DESC")
    List<Poster> findPostersForAudienceOverlapping(@Param("from") Instant from, @Param("to") Instant to, @Param("aud") Audience audience);

    @Query("SELECT p FROM Poster p WHERE p.startTime <= :now AND p.endTime > :now")
    List<Poster> findActivePostersAt(@Param("now") Instant now);
}
