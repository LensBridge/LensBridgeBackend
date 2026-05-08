package com.ibrasoft.lensbridge.repository.sql;

import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BoardEventRepository extends JpaRepository<Event, UUID> {

    List<Event> findAllByOrderByStartEpochMsAsc();

    @Query("SELECT e FROM Event e WHERE (e.audience = :aud OR e.audience = com.ibrasoft.lensbridge.model.board.Audience.BOTH) ORDER BY e.startEpochMs ASC")
    List<Event> findByAudienceOrBoth(@Param("aud") Audience audience);

    @Query("SELECT e FROM Event e WHERE (e.audience = :aud OR e.audience = com.ibrasoft.lensbridge.model.board.Audience.BOTH) AND e.startEpochMs >= :now ORDER BY e.startEpochMs ASC")
    List<Event> findUpcomingByAudienceOrBoth(@Param("aud") Audience audience, @Param("now") long now);

    @Query("SELECT e FROM Event e WHERE (e.audience = :aud OR e.audience = com.ibrasoft.lensbridge.model.board.Audience.BOTH) AND e.startEpochMs <= :rangeEnd AND e.endEpochMs >= :rangeStart ORDER BY e.startEpochMs ASC")
    List<Event> findOverlappingForAudienceOrBoth(@Param("aud") Audience audience, @Param("rangeStart") long rangeStart, @Param("rangeEnd") long rangeEnd);
}
