package com.ibrasoft.minbar.signage.repository;

import com.ibrasoft.minbar.signage.model.Audience;
import com.ibrasoft.minbar.signage.model.Poster;
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

    @Query("SELECT p FROM Poster p WHERE (p.audience = :aud OR p.audience = com.ibrasoft.minbar.signage.model.Audience.BOTH) ORDER BY p.startTime DESC")
    List<Poster> findByAudienceOrBoth(@Param("aud") Audience audience);

    @Query("SELECT p FROM Poster p WHERE (p.audience = :aud OR p.audience = com.ibrasoft.minbar.signage.model.Audience.BOTH) AND p.startTime <= :now AND p.endTime > :now ORDER BY p.startTime DESC")
    List<Poster> findActivePostersForAudienceAt(@Param("now") Instant now, @Param("aud") Audience audience);

    @Query("SELECT p FROM Poster p WHERE p.startTime <= :now AND p.endTime > :now")
    List<Poster> findActivePostersAt(@Param("now") Instant now);
}
