package com.ibrasoft.lensbridge.repository.sql;

import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.BoardEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface BoardEventRepository extends JpaRepository<BoardEvent, UUID> {

    List<BoardEvent> findAllByOrderByStartTimeAsc();

    @Query("SELECT e FROM BoardEvent e WHERE (e.audience = :aud OR e.audience = com.ibrasoft.lensbridge.model.board.Audience.BOTH) ORDER BY e.startTime ASC")
    List<BoardEvent> findByAudienceOrBoth(@Param("aud") Audience audience);

    @Query("SELECT e FROM BoardEvent e WHERE (e.audience = :aud OR e.audience = com.ibrasoft.lensbridge.model.board.Audience.BOTH) AND e.startTime >= :now ORDER BY e.startTime ASC")
    List<BoardEvent> findUpcomingByAudienceOrBoth(@Param("aud") Audience audience, @Param("now") Instant now);

    @Query("SELECT e FROM BoardEvent e WHERE (e.audience = :aud OR e.audience = com.ibrasoft.lensbridge.model.board.Audience.BOTH) AND e.startTime <= :rangeEnd AND e.endTime >= :rangeStart ORDER BY e.startTime ASC")
    List<BoardEvent> findOverlappingForAudienceOrBoth(@Param("aud") Audience audience, @Param("rangeStart") Instant rangeStart, @Param("rangeEnd") Instant rangeEnd);
}
