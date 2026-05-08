package com.ibrasoft.lensbridge.repository.sql;

import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Poster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface PosterRepository extends JpaRepository<Poster, UUID> {

    List<Poster> findAllByOrderByStartDateDesc();

    @Query("SELECT p FROM Poster p WHERE (p.audience = :aud OR p.audience = com.ibrasoft.lensbridge.model.board.Audience.BOTH) ORDER BY p.startDate DESC")
    List<Poster> findByAudienceOrBoth(@Param("aud") Audience audience);

    @Query("SELECT p FROM Poster p WHERE (p.audience = :aud OR p.audience = com.ibrasoft.lensbridge.model.board.Audience.BOTH) AND p.startDate <= :today AND p.endDate > :today ORDER BY p.startDate DESC")
    List<Poster> findActivePostersForAudienceAt(@Param("today") LocalDate today, @Param("aud") Audience audience);

    @Query("SELECT p FROM Poster p WHERE p.startDate <= :today AND p.endDate > :today")
    List<Poster> findActivePostersAt(@Param("today") LocalDate today);
}
